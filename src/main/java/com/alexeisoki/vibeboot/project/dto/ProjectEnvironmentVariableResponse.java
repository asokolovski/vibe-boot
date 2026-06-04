package com.alexeisoki.vibeboot.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectEnvironmentVariableResponse(
        UUID id,
        UUID projectId,
        String key,
        Instant createdAt
) {
}
