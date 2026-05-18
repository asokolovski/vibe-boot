package com.alexeisoki.vibeboot.deployment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TriggerDeploymentRequest(
        @NotNull
        UUID projectId
) {
}
