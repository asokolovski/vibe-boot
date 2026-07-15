package com.alexeisoki.vibeboot.deployment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileResult;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileService;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeRunResult;
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
import com.alexeisoki.vibeboot.project.ProjectSourceType;
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
    private final ComposeFileService composeFileService;

    DeploymentExecutor(
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
        this(
                deploymentRepository,
                deploymentLogService,
                projectService,
                dockerService,
                portAllocator,
                healthCheckService,
                workspaceService,
                gitService,
                environmentVariableService,
                new ComposeFileService()
        );
    }

    @Autowired
    public DeploymentExecutor(
            DeploymentRepository deploymentRepository,
            DeploymentLogService deploymentLogService,
            ProjectService projectService,
            DockerService dockerService,
            PortAllocator portAllocator,
            HealthCheckService healthCheckService,
            WorkspaceService workspaceService,
            GitService gitService,
            ProjectEnvironmentVariableService environmentVariableService,
            ComposeFileService composeFileService
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
        this.composeFileService = composeFileService;
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
            if (project.getSourceType() == ProjectSourceType.CONTAINER_IMAGE) {
                pullDockerImage(deploymentId, deployment, project);
                int hostPort = allocateHostPort();
                Map<String, String> environmentVariables =
                        loadEnvironmentVariables(deploymentId, deployment.getProjectId());
                runDockerContainer(deploymentId, deployment, project, hostPort, environmentVariables);
            } else if (project.getSourceType() == ProjectSourceType.DOCKER_COMPOSE) {
                workspace = createWorkspace(deploymentId);
                Path sourceDirectory = workspace.resolve("source");
                cloneRepository(deploymentId, project, sourceDirectory);
                int hostPort = allocateHostPort();
                Map<String, String> environmentVariables =
                        loadEnvironmentVariables(deploymentId, deployment.getProjectId());
                runDockerCompose(deploymentId, deployment, project, sourceDirectory, hostPort, environmentVariables);
            } else {
                workspace = createWorkspace(deploymentId);
                Path sourceDirectory = workspace.resolve("source");
                cloneRepository(deploymentId, project, sourceDirectory);
                buildDockerImage(deploymentId, deployment, project, sourceDirectory);
                int hostPort = allocateHostPort();
                Map<String, String> environmentVariables =
                        loadEnvironmentVariables(deploymentId, deployment.getProjectId());
                runDockerContainer(deploymentId, deployment, project, hostPort, environmentVariables);
            }
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

    private void pullDockerImage(UUID deploymentId, Deployment deployment, Project project) {
        String imageName = imageNameForContainerProject(deployment, project);
        deploymentLogService.appendLog(deploymentId, "Pulling Docker image: " + imageName);

        String pullOutput = dockerService.pullImage(imageName);
        appendCommandOutput(deploymentId, pullOutput);

        deployment.recordDockerImage(imageName);
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Docker image pulled: " + imageName);
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

    private void runDockerCompose(
            UUID deploymentId,
            Deployment deployment,
            Project project,
            Path sourceDirectory,
            int hostPort,
            Map<String, String> environmentVariables
    ) {
        deploymentLogService.appendLog(deploymentId, "Preparing Docker Compose file");
        ComposeFileResult composeFileResult = composeFileService.createVibeBootComposeFile(
                project,
                sourceDirectory,
                hostPort
        );
        deploymentLogService.appendLog(
                deploymentId,
                "Generated Docker Compose file: " + composeFileResult.composeFile().getFileName()
        );

        deploymentLogService.appendLog(deploymentId, "Starting Docker Compose project");
        ComposeRunResult runResult = dockerService.runCompose(
                deploymentId,
                project,
                composeFileResult.composeFile(),
                hostPort,
                composeFileResult.containerPort(),
                environmentVariables
        );
        appendCommandOutput(deploymentId, runResult.output());

        deployment.recordComposeRuntime(
                runResult.composeProjectName(),
                runResult.primaryServiceName(),
                runResult.hostPort(),
                runResult.containerPort(),
                runResult.deploymentUrl()
        );
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Docker Compose project started: " + runResult.composeProjectName());
        deploymentLogService.appendLog(deploymentId, "Primary service: " + runResult.primaryServiceName());
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

    private String imageNameForContainerProject(Deployment deployment, Project project) {
        String containerRegistry = project.getContainerRegistry();
        if (containerRegistry == null || containerRegistry.isBlank()) {
            throw new IllegalArgumentException("containerRegistry must not be blank");
        }
        String imagetag = defaultIfBlank(deployment.getImageTag(), "latest");
        String colonOrAt = imagetag.startsWith("sha256:") ? "@" : ":"; 

        return stripTrailingSlash(containerRegistry) + colonOrAt + imagetag;
    }

    private String stripTrailingSlash(String value) {
        String strippedValue = value;
        while (strippedValue.endsWith("/")) {
            strippedValue = strippedValue.substring(0, strippedValue.length() - 1);
        }
        return strippedValue;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
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
