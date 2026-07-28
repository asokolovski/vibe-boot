package com.alexeisoki.vibeboot.deployment.strategy;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.deployment.Deployment;
import com.alexeisoki.vibeboot.deployment.DeploymentLogService;
import com.alexeisoki.vibeboot.deployment.DeploymentRepository;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileResult;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileService;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeRunResult;
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
public class DockerComposeDeploymentStrategy implements DeploymentStrategy {
    private static final int MAX_LOG_MESSAGE_LENGTH = 4000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService deploymentLogService;
    private final DockerService dockerService;
    private final PortAllocator portAllocator;
    private final WorkspaceService workspaceService;
    private final GitService gitService;
    private final ProjectEnvironmentVariableService environmentVariableService;
    private final ComposeFileService composeFileService;

    public DockerComposeDeploymentStrategy(
            DeploymentRepository deploymentRepository,
            DeploymentLogService deploymentLogService,
            DockerService dockerService,
            PortAllocator portAllocator,
            WorkspaceService workspaceService,
            GitService gitService,
            ProjectEnvironmentVariableService environmentVariableService,
            ComposeFileService composeFileService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogService = deploymentLogService;
        this.dockerService = dockerService;
        this.portAllocator = portAllocator;
        this.workspaceService = workspaceService;
        this.gitService = gitService;
        this.environmentVariableService = environmentVariableService;
        this.composeFileService = composeFileService;
    }

    @Override
    public ProjectSourceType sourceType() {
        return ProjectSourceType.DOCKER_COMPOSE;
    }

    @Override
    public void deploy(DeploymentExecutionContext context) {
        UUID deploymentId = context.deploymentId();
        Deployment deployment = context.deployment();
        Project project = context.project();

        Path workspace = createWorkspace(context);
        Path sourceDirectory = workspace.resolve("source");
        cloneRepository(deploymentId, project, sourceDirectory);

        int hostPort = allocateHostPort();
        Map<String, String> environmentVariables =
                loadEnvironmentVariables(deploymentId, deployment.getProjectId());

        runDockerCompose(
                deploymentId,
                deployment,
                project,
                sourceDirectory,
                hostPort,
                environmentVariables
        );
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
        deploymentLogService.appendLog(
                deploymentId,
                "Docker Compose project started: " + runResult.composeProjectName()
        );
        deploymentLogService.appendLog(deploymentId, "Primary service: " + runResult.primaryServiceName());
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
