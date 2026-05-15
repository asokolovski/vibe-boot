package com.alexeisoki.vibeboot.deployment.dto;

import java.util.UUID;

public record TriggerDeploymentRequest(
        UUID projectId
) {
}