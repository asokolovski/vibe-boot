package com.alexeisoki.vibeboot.deployment.strategy;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.deployment.Deployment;
import com.alexeisoki.vibeboot.deployment.DeploymentLogService;
import com.alexeisoki.vibeboot.deployment.DeploymentRepository;
import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.GitCloneResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
import com.alexeisoki.vibeboot.project.ProjectSourceType;

@Component
public class GitHubRepositoryDeploymentStrategy implements DeploymentStrategy {
    private static final int MAX_LOG_MESSAGE_LENGTH = 4000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService deploymentLogService;
    private final DockerService dockerService;
    private final PortAllocator portAllocator;
    private final WorkspaceService workspaceService;
    private final GitService gitService;
    private final ProjectEnvironmentVariableService environmentVariableService;

    public GitHubRepositoryDeploymentStrategy(
            DeploymentRepository deploymentRepository,
            DeploymentLogService deploymentLogService,
            DockerService dockerService,
            PortAllocator portAllocator,
            WorkspaceService workspaceService,
            GitService gitService,
            ProjectEnvironmentVariableService environmentVariableService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogService = deploymentLogService;
        this.dockerService = dockerService;
        this.portAllocator = portAllocator;
        this.workspaceService = workspaceService;
        this.gitService = gitService;
        this.environmentVariableService = environmentVariableService;
    }

    @Override
    public ProjectSourceType sourceType() {
        return ProjectSourceType.GITHUB_REPOSITORY;
    }

    @Override
    public void deploy(DeploymentExecutionContext context) {
        UUID deploymentId = context.deploymentId();
        Deployment deployment = context.deployment();
        Project project = context.project();

        Path workspace = createWorkspace(context);
        Path sourceDirectory = workspace.resolve("source");
        cloneRepository(deploymentId, project, sourceDirectory);
        buildDockerImage(deploymentId, deployment, project, sourceDirectory);

        int hostPort = allocateHostPort();
        Map<String, String> environmentVariables =
                loadEnvironmentVariables(deploymentId, deployment.getProjectId());

        runDockerContainer(deploymentId, deployment, project, hostPort, environmentVariables);
    }

    private Path createWorkspace(DeploymentExecutionContext context) {
        Path workspace = workspaceService.createWorkspace(context.deploymentId());
        context.registerWorkspace(workspace);
        deploymentLogService.appendLog(context.deploymentId(), "Created workspace");
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

    private void buildDockerImage(
            UUID deploymentId,
            Deployment deployment,
            Project project,
            Path sourceDirectory
    ) {
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

    private int allocateHostPort() {
        try {
            return portAllocator.allocatePort();
        } catch (IllegalStateException exception) {
            throw new DockerServiceException(exception.getMessage());
        }
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
