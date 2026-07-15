package com.alexeisoki.vibeboot.project.dto;

import java.time.Instant;
import java.util.UUID;

import com.alexeisoki.vibeboot.project.ProjectSourceType;

public record ProjectResponse(
        UUID id,
        String name,
        String repositoryUrl,
        ProjectSourceType sourceType,
        String containerRegistry,
        String branch,
        String dockerfilePath,
        Integer containerPort,
        String healthCheckPath,
        String composeFilePath,
        String primaryServiceName,
        UUID ownerUserId,
        Instant createdAt
) {
    public ProjectResponse(
            UUID id,
            String name,
            String repositoryUrl,
            ProjectSourceType sourceType,
            String containerRegistry,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath,
            UUID ownerUserId,
            Instant createdAt
    ) {
        this(
                id,
                name,
                repositoryUrl,
                sourceType,
                containerRegistry,
                branch,
                dockerfilePath,
                containerPort,
                healthCheckPath,
                null,
                null,
                ownerUserId,
                createdAt
        );
    }
}
