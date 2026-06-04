package com.alexeisoki.vibeboot.deployment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.GitCloneResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitService;
import com.alexeisoki.vibeboot.deployment.runtime.GitServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckResult;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
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
    private final WorkspaceService workspaceService;
    private final GitService gitService;
    private final ProjectEnvironmentVariableService environmentVariableService;

    public DeploymentExecutor(
            DeploymentRepository deploymentRepository,
            DeploymentLogService deploymentLogService,
            ProjectService projectService,
            DockerService dockerService,
            PortAllocator portAllocator,
            HealthCheckService healthCheckService,
            WorkspaceService workspaceService,
            GitService gitService,
            ProjectEnvironmentVariableService environmentVariableService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogService = deploymentLogService;
        this.projectService = projectService;
        this.dockerService = dockerService;
        this.portAllocator = portAllocator;
        this.healthCheckService = healthCheckService;
        this.workspaceService = workspaceService;
        this.gitService = gitService;
        this.environmentVariableService = environmentVariableService;
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

        Path workspace = null;
        try {
            Project project = projectService.getProjectOrThrow(deployment.getProjectId());
            workspace = createWorkspace(deploymentId);
            Path sourceDirectory = workspace.resolve("source");
            cloneRepository(deploymentId, project, sourceDirectory);
            buildDockerImage(deploymentId, deployment, project, sourceDirectory);
            int hostPort = allocateHostPort();
            Map<String, String> environmentVariables =
                    loadEnvironmentVariables(deploymentId, deployment.getProjectId());
            runDockerContainer(deploymentId, deployment, project, hostPort, environmentVariables);
            finishAfterHealthCheck(deploymentId, deployment, project);
        } catch (RuntimeException exception) {
            failDeployment(deploymentId, deployment, exception);
        } finally {
            cleanupWorkspace(deploymentId, workspace);
        }
    }

    private Path createWorkspace(UUID deploymentId) {
        Path workspace = workspaceService.createWorkspace(deploymentId);
        deploymentLogService.appendLog(deploymentId, "Created workspace");
        return workspace;
    }

    private void cloneRepository(UUID deploymentId, Project project, Path sourceDirectory) {
        deploymentLogService.appendLog(deploymentId, "Cloning repository");
        GitCloneResult cloneResult = gitService.cloneRepository(
                project.getRepositoryUrl(),
                project.getBranch(),
                sourceDirectory
        );
        appendCommandOutput(deploymentId, cloneResult.output());
        deploymentLogService.appendLog(deploymentId, "Repository cloned successfully");
    }

    private void buildDockerImage(UUID deploymentId, Deployment deployment, Project project, Path sourceDirectory) {
        deploymentLogService.appendLog(deploymentId, "Building Docker image");

        DockerBuildResult buildResult = dockerService.buildImage(
                deploymentId,
                project,
                sourceDirectory
        );
        appendCommandOutput(deploymentId, buildResult.output());

        deployment.recordDockerImage(buildResult.imageName());
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Docker image built: " + buildResult.imageName());
    }

    private Map<String, String> loadEnvironmentVariables(UUID deploymentId, UUID projectId) {
        deploymentLogService.appendLog(deploymentId, "Loading project environment variables");
        Map<String, String> environmentVariables =
                environmentVariableService.getDecryptedEnvVarsForProject(projectId);
        deploymentLogService.appendLog(
                deploymentId,
                "Loaded " + environmentVariables.size() + " project environment variable(s)"
        );
        return environmentVariables;
    }

    private void runDockerContainer(
            UUID deploymentId,
            Deployment deployment,
            Project project,
            int hostPort,
            Map<String, String> environmentVariables
    ) {
        deploymentLogService.appendLog(deploymentId, "Starting Docker container");

        DockerRunResult runResult = dockerService.runContainer(
                deploymentId,
                project,
                deployment.getImageName(),
                hostPort,
                environmentVariables
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

    private void failDeployment(UUID deploymentId, Deployment deployment, RuntimeException exception) {
        appendFailureOutput(deploymentId, exception);

        if (deployment.getContainerId() != null && !deployment.getContainerId().isBlank()) {
            cleanupUnhealthyContainer(deploymentId, deployment);
        }

        deployment.markFinished(DeploymentStatus.FAILED);
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, failureMessage(exception));
        deploymentLogService.appendLog(deploymentId, "Deployment failed");
    }

    private void cleanupWorkspace(UUID deploymentId, Path workspace) {
        if (workspace == null) {
            return;
        }

        try {
            workspaceService.cleanupWorkspace(workspace);
            deploymentLogService.appendLog(deploymentId, "Workspace cleaned up");
        } catch (RuntimeException exception) {
            deploymentLogService.appendLog(
                    deploymentId,
                    "Could not clean up workspace: " + failureMessage(exception)
            );
        }
    }

    private void cleanupUnhealthyContainer(UUID deploymentId, Deployment deployment) {
        String containerId = deployment.getContainerId();
        if (containerId == null || containerId.isBlank()) {
            return;
        }

        deploymentLogService.appendLog(deploymentId, "Collecting unhealthy container logs");
        try {
            appendCommandOutput(deploymentId, dockerService.getContainerLogs(containerId));
        } catch (DockerServiceException exception) {
            appendCommandOutput(deploymentId, exception.commandOutput());
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
            appendCommandOutput(deploymentId, exception.commandOutput());
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

    private void appendFailureOutput(UUID deploymentId, RuntimeException exception) {
        if (exception instanceof DockerServiceException dockerServiceException) {
            appendCommandOutput(deploymentId, dockerServiceException.commandOutput());
        }

        if (exception instanceof GitServiceException gitServiceException) {
            appendCommandOutput(deploymentId, gitServiceException.commandOutput());
        }
    }

    private String failureMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void appendCommandOutput(UUID deploymentId, String output) {
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
