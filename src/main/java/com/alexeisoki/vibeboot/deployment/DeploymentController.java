package com.alexeisoki.vibeboot.deployment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {
    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    public ResponseEntity<DeploymentResponse> triggerDeployment(@RequestBody TriggerDeploymentRequest request) {
        DeploymentResponse response = deploymentService.triggerDeployment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{deploymentId}")
    public DeploymentResponse getDeployment(@PathVariable UUID deploymentId) {
        return deploymentService.getDeploymentOrThrow(deploymentId);
    }
     
}
