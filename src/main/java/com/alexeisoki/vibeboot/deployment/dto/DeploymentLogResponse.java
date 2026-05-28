package com.alexeisoki.vibeboot.deployment.dto;

import java.time.Instant;

public record DeploymentLogResponse(
        String message,
        Instant createdAt
) {
}
