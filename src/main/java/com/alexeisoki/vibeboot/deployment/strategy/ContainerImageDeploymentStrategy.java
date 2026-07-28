package com.alexeisoki.vibeboot.deployment.strategy;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.deployment.Deployment;
import com.alexeisoki.vibeboot.deployment.DeploymentLogService;
import com.alexeisoki.vibeboot.deployment.DeploymentRepository;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectSourceType;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;



@Component
public class ContainerImageDeploymentStrategy implements DeploymentStrategy {
    private static final int MAX_LOG_MESSAGE_LENGTH = 4000;

    private final DeploymentRepository deploymentRepository;
    private final DockerService dockerService;
    private final DeploymentLogService deploymentLogService;
    private final PortAllocator portAllocator;
    private final ProjectEnvironmentVariableService environmentVariableService;


    public ContainerImageDeploymentStrategy(
        DeploymentRepository deploymentRepository,
        DockerService dockerService, 
        DeploymentLogService deploymentLogService,
        PortAllocator portAllocator,
        ProjectEnvironmentVariableService environmentVariableService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.dockerService = dockerService;
        this.deploymentLogService = deploymentLogService;
        this.portAllocator = portAllocator;
        this.environmentVariableService = environmentVariableService;
    }

    @Override
    public ProjectSourceType sourceType() {
        return ProjectSourceType.CONTAINER_IMAGE;
    }

    @Override
    public void deploy(DeploymentExecutionContext context) {
        Project project = context.project();
        Deployment deployment = context.deployment();
        UUID deploymentId = context.deploymentId();

        pullDockerImage(deploymentId, deployment, project);

        int hostPort = allocateHostPort();

        Map<String, String> environmentVariables =
                        loadEnvironmentVariables(deploymentId, deployment.getProjectId());

        runDockerContainer(deploymentId, deployment, project, hostPort, environmentVariables);
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

    private void pullDockerImage(UUID deploymentId, Deployment deployment, Project project) {
        String imageName = imageNameForContainerProject(deployment, project);
        deploymentLogService.appendLog(deploymentId, "Pulling Docker image: " + imageName);

        String pullOutput = dockerService.pullImage(imageName);
        appendCommandOutput(deploymentId, pullOutput);

        deployment.recordDockerImage(imageName);
        deploymentRepository.save(deployment);
        deploymentLogService.appendLog(deploymentId, "Docker image pulled: " + imageName);
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
