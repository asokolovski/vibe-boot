package com.alexeisoki.vibeboot.deployment.dto;

import com.alexeisoki.vibeboot.deployment.DeploymentStatus;

import java.time.Instant;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID projectId,
        DeploymentStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}