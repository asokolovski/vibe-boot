package com.alexeisoki.vibeboot.deployment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TriggerDeploymentRequest(
        @NotNull
        UUID projectId,
        @Pattern(
                regexp = "^([A-Za-z0-9_][A-Za-z0-9_.-]{0,127}|sha256:[a-fA-F0-9]{64})$",
                message = "must be a valid Docker image tag"
        )
        String imageTag
) {
    public TriggerDeploymentRequest(UUID projectId) {
        this(projectId, null);
    }
}
