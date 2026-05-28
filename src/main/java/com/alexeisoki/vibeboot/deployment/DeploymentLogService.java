package com.alexeisoki.vibeboot.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentLogResponse;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Service
public class DeploymentLogService {

    private final DeploymentLogRepository deploymentLogRepository;
    private final DeploymentRepository deploymentRepository;

    public DeploymentLogService(
            DeploymentLogRepository deploymentLogRepository,
            DeploymentRepository deploymentRepository
    ) {
        this.deploymentLogRepository = deploymentLogRepository;
        this.deploymentRepository = deploymentRepository;
    }

    public void appendLog(UUID deploymentId, String message) {
        verifyDeploymentExists(deploymentId);
        deploymentLogRepository.save(new DeploymentLog(deploymentId, message));
    }

    public List<DeploymentLogResponse> getLogs(UUID deploymentId) {
        verifyDeploymentExists(deploymentId);

        List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
        List<DeploymentLogResponse> responses = new ArrayList<>();
        for (DeploymentLog log : logs) {
            responses.add(toResponse(log));
        }
        return responses;
    }

    private void verifyDeploymentExists(UUID deploymentId) {
        if (!deploymentRepository.existsById(deploymentId)) {
            throw new ResourceNotFoundException("Deployment not found");
        }
    }

    private DeploymentLogResponse toResponse(DeploymentLog log) {
        return new DeploymentLogResponse(
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
