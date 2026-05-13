package com.alexeisoki.vibeboot.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String repositoryUrl,
        String branch,
        String runCommand,
        Instant createdAt
) {
}
