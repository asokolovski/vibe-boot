package com.alexeisoki.vibeboot.deployment;


import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.project.ProjectService;



@Service
public class DeploymentService {
    private final DeploymentRepository deploymentRepository;
    private final ProjectService projectService;

    public DeploymentService(DeploymentRepository deploymentRepository, ProjectService projectService) {
        this.deploymentRepository = deploymentRepository;
        this.projectService = projectService;
    }

    public DeploymentResponse triggerDeployment(TriggerDeploymentRequest request) {
        //Verify project exists
        projectService.getProjectOrThrow(request.projectId());

        //create new deployment and save it to the database
        Deployment deployment = new Deployment(request.projectId());
        Deployment savedDeployment = deploymentRepository.save(deployment);
        
        return toResponse(savedDeployment);
    }

    public DeploymentResponse getDeploymentOrThrow(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found"));

        return toResponse(deployment);
    }


    private DeploymentResponse toResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProjectId(),
                deployment.getStatus(),
                deployment.getCreatedAt(),
                deployment.getStartedAt(),
                deployment.getFinishedAt()
        );
    }


}
