package com.alexeisoki.vibeboot.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.deployment.queue.DeploymentQueuePublisher;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.shared.ResourceConflictException;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Service
public class DeploymentService {
    private final DeploymentRepository deploymentRepository;
    private final ProjectService projectService;
    private final DeploymentQueuePublisher deploymentQueuePublisher;
    private final DockerService dockerService;
    private final DeploymentLogService deploymentLogService;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            ProjectService projectService,
            DeploymentQueuePublisher deploymentQueuePublisher,
            DockerService dockerService,
            DeploymentLogService deploymentLogService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.projectService = projectService;
        this.deploymentQueuePublisher = deploymentQueuePublisher;
        this.dockerService = dockerService;
        this.deploymentLogService = deploymentLogService;
    }

    public DeploymentResponse triggerDeployment(TriggerDeploymentRequest request) {
        //Verify project exists
        projectService.getProjectOrThrow(request.projectId());

        //create new deployment and save it to the database
        Deployment deployment = new Deployment(request.projectId());
        Deployment savedDeployment = deploymentRepository.save(deployment);
        deploymentQueuePublisher.publishDeploymentRequested(savedDeployment.getId());

        return toResponse(savedDeployment);
    }

    public DeploymentResponse getDeploymentOrThrow(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found"));

        return toResponse(deployment);
    }

    public List<DeploymentResponse> getDeploymentsForProject(UUID projectId) {
        projectService.getProjectOrThrow(projectId);
        List<Deployment> deployments = deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<DeploymentResponse> responses = new ArrayList<>();
        for (Deployment deployment : deployments) {
            responses.add(toResponse(deployment));
        }
        return responses;
    }

    public DeploymentResponse stopDeployment(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found"));

        if (deployment.getStatus() == DeploymentStatus.STOPPED) {
            throw new ResourceConflictException("Deployment is already stopped");
        }

        String containerId = deployment.getContainerId();
        if (containerId == null || containerId.isBlank()) {
            throw new ResourceConflictException("Deployment has no running container to stop");
        }

        dockerService.stopContainer(containerId);
        deployment.markStopped();
        Deployment savedDeployment = deploymentRepository.save(deployment);

        deploymentLogService.appendLog(deploymentId, "Docker container stopped: " + containerId);
        deploymentLogService.appendLog(deploymentId, "Deployment stopped");

        return toResponse(savedDeployment);
    }

    private DeploymentResponse toResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProjectId(),
                deployment.getStatus(),
                deployment.getCreatedAt(),
                deployment.getStartedAt(),
                deployment.getFinishedAt(),
                deployment.getImageName(),
                deployment.getContainerId(),
                deployment.getHostPort(),
                deployment.getContainerPort(),
                deployment.getDeploymentUrl()
        );
    }
}
