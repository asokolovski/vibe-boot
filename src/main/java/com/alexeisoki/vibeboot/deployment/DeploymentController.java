package com.alexeisoki.vibeboot.deployment;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentLogResponse;
import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {
    private final DeploymentService deploymentService;
    private final DeploymentLogService deploymentLogService;

    public DeploymentController(
            DeploymentService deploymentService,
            DeploymentLogService deploymentLogService
    ) {
        this.deploymentService = deploymentService;
        this.deploymentLogService = deploymentLogService;
    }

    @PostMapping
    public ResponseEntity<DeploymentResponse> triggerDeployment(@Valid @RequestBody TriggerDeploymentRequest request) {
        DeploymentResponse response = deploymentService.triggerDeployment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{deploymentId}")
    public DeploymentResponse getDeployment(@PathVariable UUID deploymentId) {
        return deploymentService.getDeploymentOrThrow(deploymentId);
    }

    @PostMapping("/{deploymentId}/stop")
    public DeploymentResponse stopDeployment(@PathVariable UUID deploymentId) {
        return deploymentService.stopDeployment(deploymentId);
    }

    @GetMapping("/{deploymentId}/logs")
    public List<DeploymentLogResponse> getDeploymentLogs(@PathVariable UUID deploymentId) {
        return deploymentLogService.getLogs(deploymentId);
    }
}
