package com.alexeisoki.vibeboot.deployment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.GitServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckResult;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckService;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.deployment.strategy.DeploymentExecutionContext;
import com.alexeisoki.vibeboot.deployment.strategy.DeploymentStrategy;
import com.alexeisoki.vibeboot.deployment.strategy.DeploymentStrategyResolver;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Component
public class DeploymentExecutor {
    private static final int MAX_LOG_MESSAGE_LENGTH = 4000;
    private static final int MAX_ATTEMPTS = 3;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService deploymentLogService;
    private final ProjectService projectService;
    private final DockerService dockerService;
    private final HealthCheckService healthCheckService;
    private final WorkspaceService workspaceService;
    private final DeploymentStrategyResolver strategyResolver;


    @Autowired
    public DeploymentExecutor(
            DeploymentRepository deploymentRepository,
            DeploymentLogService deploymentLogService,
            ProjectService projectService,
            DockerService dockerService,
            HealthCheckService healthCheckService,
            WorkspaceService workspaceService,
            DeploymentStrategyResolver strategyResolver
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogService = deploymentLogService;
        this.projectService = projectService;
        this.dockerService = dockerService;
        this.healthCheckService = healthCheckService;
        this.workspaceService = workspaceService;
        this.strategyResolver = strategyResolver;
    }

    public void execute(UUID deploymentId) {
        execute(deploymentId, false);
    }

    public void execute(UUID deploymentId, boolean redelivered) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found"));

        if (isTerminal(deployment.getStatus())) {
            return;
        }

        if (deployment.getStatus() == DeploymentStatus.RUNNING) {
            if (!redelivered) {
                return;
            }

            if (deployment.getAttemptCount() >= MAX_ATTEMPTS) {
                failDeployment(
                        deploymentId,
                        deployment,
                        new IllegalStateException("Deployment worker was interrupted too many times")
                );
                return;
            }

            recoverInterruptedDeployment(deploymentId, deployment);
        }

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

        
        DeploymentExecutionContext context = null;

        try {
            Project project = projectService.getProjectOrThrow(deployment.getProjectId());
            context = new DeploymentExecutionContext(deploymentId, deployment, project);

            DeploymentStrategy strategy = strategyResolver.resolve(project.getSourceType());

            strategy.deploy(context);

            finishAfterHealthCheck(deploymentId, deployment, project);

        } catch(RuntimeException exception) {
            failDeployment(deploymentId, deployment, exception);

        } finally {
            Path workspace = context == null ? null : context.workspace();
            cleanupWorkspace(deploymentId, workspace);
        }
    }

    private boolean isTerminal(DeploymentStatus status) {
        return status == DeploymentStatus.SUCCESS
                || status == DeploymentStatus.FAILED
                || status == DeploymentStatus.STOPPED;
    }

    private void recoverInterruptedDeployment(UUID deploymentId, Deployment deployment) {
        deploymentLogService.appendLog(
                deploymentId,
                "Previous deployment attempt was interrupted; retrying"
        );
        cleanupUnhealthyRuntime(deploymentId, deployment);
        deployment.prepareForRetry();
        deploymentRepository.save(deployment);
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
        cleanupUnhealthyRuntime(deploymentId, deployment);
        deployment.markFinished(DeploymentStatus.FAILED);
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Deployment failed");
    }

    private void failDeployment(UUID deploymentId, Deployment deployment, RuntimeException exception) {
        appendFailureOutput(deploymentId, exception);

        cleanupUnhealthyRuntime(deploymentId, deployment);

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

    private void cleanupUnhealthyRuntime(UUID deploymentId, Deployment deployment) {
        if (deployment.getRuntimeType() == DeploymentRuntimeType.DOCKER_COMPOSE) {
            cleanupUnhealthyComposeProject(deploymentId, deployment);
            return;
        }

        cleanupUnhealthyContainer(deploymentId, deployment);
    }

    private void cleanupUnhealthyComposeProject(UUID deploymentId, Deployment deployment) {
        String composeProjectName = deployment.getComposeProjectName();
        if (composeProjectName == null || composeProjectName.isBlank()) {
            return;
        }

        deploymentLogService.appendLog(deploymentId, "Collecting unhealthy Docker Compose logs");
        try {
            appendCommandOutput(deploymentId, dockerService.getComposeLogs(composeProjectName));
        } catch (DockerServiceException exception) {
            appendCommandOutput(deploymentId, exception.commandOutput());
            deploymentLogService.appendLog(
                    deploymentId,
                    "Could not collect unhealthy Docker Compose logs: " + exception.getMessage()
            );
        }

        deploymentLogService.appendLog(deploymentId, "Stopping unhealthy Docker Compose project: " + composeProjectName);
        try {
            dockerService.stopComposeProject(composeProjectName);
            deploymentLogService.appendLog(deploymentId, "Unhealthy Docker Compose project stopped: " + composeProjectName);
        } catch (DockerServiceException exception) {
            appendCommandOutput(deploymentId, exception.commandOutput());
            deploymentLogService.appendLog(
                    deploymentId,
                    "Could not stop unhealthy Docker Compose project: " + exception.getMessage()
            );
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
