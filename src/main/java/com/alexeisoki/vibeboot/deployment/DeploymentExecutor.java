package com.alexeisoki.vibeboot.deployment;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckResult;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Component
public class DeploymentExecutor {
    private static final int MAX_LOG_MESSAGE_LENGTH = 4000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService deploymentLogService;
    private final ProjectService projectService;
    private final DockerService dockerService;
    private final PortAllocator portAllocator;
    private final HealthCheckService healthCheckService;

    public DeploymentExecutor(
            DeploymentRepository deploymentRepository,
            DeploymentLogService deploymentLogService,
            ProjectService projectService,
            DockerService dockerService,
            PortAllocator portAllocator,
            HealthCheckService healthCheckService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogService = deploymentLogService;
        this.projectService = projectService;
        this.dockerService = dockerService;
        this.portAllocator = portAllocator;
        this.healthCheckService = healthCheckService;
    }

    public void execute(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found"));

        if (deployment.getStatus() != DeploymentStatus.QUEUED) {
            return;
        }

        Instant startedAt = Instant.now();
        int startedDeployments = deploymentRepository.markRunningIfQueued(
                deploymentId,
                startedAt,
                DeploymentStatus.QUEUED,
                DeploymentStatus.RUNNING
        );

        if (startedDeployments == 0) {
            return;
        }

        deployment.markRunning(startedAt);
        deploymentLogService.appendLog(deploymentId, "Deployment started");

        Project project = projectService.getProjectOrThrow(deployment.getProjectId());
        try {
            buildDockerImage(deploymentId, deployment, project);
            runDockerContainer(deploymentId, deployment, project);
        } catch (DockerServiceException exception) {
            appendDockerOutput(deploymentId, exception.commandOutput());
            deployment.markFinished(DeploymentStatus.FAILED);
            deploymentRepository.save(deployment);
            deploymentLogService.appendLog(deploymentId, exception.getMessage());
            deploymentLogService.appendLog(deploymentId, "Deployment failed");
            return;
        }

        finishAfterHealthCheck(deploymentId, deployment, project);
    }

    private void buildDockerImage(UUID deploymentId, Deployment deployment, Project project) {
        deploymentLogService.appendLog(deploymentId, "Building Docker image");

        DockerBuildResult buildResult = dockerService.buildImage(
                deploymentId,
                project,
                projectDirectory(project)
        );
        appendDockerOutput(deploymentId, buildResult.output());

        deployment.recordDockerImage(buildResult.imageName());
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Docker image built: " + buildResult.imageName());
    }

    private void runDockerContainer(UUID deploymentId, Deployment deployment, Project project) {
        deploymentLogService.appendLog(deploymentId, "Starting Docker container");

        int hostPort = allocateHostPort();
        DockerRunResult runResult = dockerService.runContainer(
                deploymentId,
                project,
                deployment.getImageName(),
                hostPort
        );

        deployment.recordDockerRuntime(
                deployment.getImageName(),
                runResult.containerId(),
                runResult.hostPort(),
                runResult.containerPort(),
                runResult.deploymentUrl()
        );
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Docker container started: " + runResult.containerId());
        deploymentLogService.appendLog(deploymentId, "Deployment URL: " + runResult.deploymentUrl());
    }

    private void finishAfterHealthCheck(UUID deploymentId, Deployment deployment, Project project) {
        deploymentLogService.appendLog(
                deploymentId,
                "Running health check: " + deployment.getDeploymentUrl() + project.getHealthCheckPath()
        );

        HealthCheckResult healthCheckResult = healthCheckService.waitUntilHealthy(
                deployment.getDeploymentUrl(),
                project.getHealthCheckPath()
        );

        if (healthCheckResult.healthy()) {
            deploymentLogService.appendLog(
                    deploymentId,
                    "Health check succeeded after " + healthCheckResult.attempts() + " attempt(s)"
            );
            deployment.markFinished(DeploymentStatus.SUCCESS);
            deploymentRepository.save(deployment);
            deploymentLogService.appendLog(deploymentId, "Deployment succeeded");
            return;
        }

        deploymentLogService.appendLog(
                deploymentId,
                "Health check failed after " + healthCheckResult.attempts() + " attempt(s)"
        );
        cleanupUnhealthyContainer(deploymentId, deployment);
        deployment.markFinished(DeploymentStatus.FAILED);
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Deployment failed");
    }

    private void cleanupUnhealthyContainer(UUID deploymentId, Deployment deployment) {
        String containerId = deployment.getContainerId();
        if (containerId == null || containerId.isBlank()) {
            return;
        }

        deploymentLogService.appendLog(deploymentId, "Collecting unhealthy container logs");
        try {
            appendDockerOutput(deploymentId, dockerService.getContainerLogs(containerId));
        } catch (DockerServiceException exception) {
            appendDockerOutput(deploymentId, exception.commandOutput());
            deploymentLogService.appendLog(
                    deploymentId,
                    "Could not collect unhealthy container logs: " + exception.getMessage()
            );
        }

        deploymentLogService.appendLog(deploymentId, "Stopping unhealthy container: " + containerId);
        try {
            dockerService.stopContainer(containerId);
            deploymentLogService.appendLog(deploymentId, "Unhealthy container stopped: " + containerId);
        } catch (DockerServiceException exception) {
            appendDockerOutput(deploymentId, exception.commandOutput());
            deploymentLogService.appendLog(
                    deploymentId,
                    "Could not stop unhealthy container: " + exception.getMessage()
            );
        }
    }

    private int allocateHostPort() {
        try {
            return portAllocator.allocatePort();
        } catch (IllegalStateException exception) {
            throw new DockerServiceException(exception.getMessage());
        }
    }

    private Path projectDirectory(Project project) {
        String localPath = project.getLocalPath();
        if (localPath == null || localPath.isBlank()) {
            throw new DockerServiceException("Project localPath is required for Docker build");
        }

        try {
            return Path.of(localPath);
        } catch (InvalidPathException exception) {
            throw new DockerServiceException("Project localPath is invalid: " + localPath);
        }
    }

    private void appendDockerOutput(UUID deploymentId, String output) {
        if (output == null || output.isBlank()) {
            return;
        }

        String normalizedOutput = output.trim();
        for (int start = 0; start < normalizedOutput.length(); start += MAX_LOG_MESSAGE_LENGTH) {
            int end = Math.min(start + MAX_LOG_MESSAGE_LENGTH, normalizedOutput.length());
            deploymentLogService.appendLog(deploymentId, normalizedOutput.substring(start, end));
        }
    }

}
