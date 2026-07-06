package com.alexeisoki.vibeboot.project.dto;

import java.util.regex.Pattern;

import com.alexeisoki.vibeboot.project.ProjectSourceType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

//record just gives us a simple immutable data class with a constructor, getters, equals, hashCode, and toString all generated for us.
public record CreateProjectRequest(
        @NotBlank
        String name,
        String repositoryUrl,
        ProjectSourceType sourceType,
        String containerRegistry,
        String branch,
        String dockerfilePath,
        @Min(1)
        @Max(65535)
        Integer containerPort,
        String healthCheckPath
) {
    private static final Pattern PUBLIC_GITHUB_REPOSITORY_URL = Pattern.compile(
            "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$"
    );
    private static final Pattern PUBLIC_GITHUB_CONTAINER_REGISTRY = Pattern.compile(
            "^ghcr\\.io/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*$"
    );

    public CreateProjectRequest(String name, String repositoryUrl) {
        this(name, repositoryUrl, null, null, null, null, null, null);
    }

    public CreateProjectRequest(
            String name,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath
    ) {
        this(name, repositoryUrl, null, null, branch, dockerfilePath, containerPort, healthCheckPath);
    }

    @AssertTrue(message = "repositoryUrl must be a public HTTPS GitHub repository URL")
    public boolean isRepositoryUrlValidForSourceType() {
        if (resolvedSourceType() != ProjectSourceType.GITHUB_REPOSITORY) {
            return true;
        }

        return repositoryUrl != null
                && !repositoryUrl.isBlank()
                && PUBLIC_GITHUB_REPOSITORY_URL.matcher(repositoryUrl).matches();
    }

    @AssertTrue(message = "containerRegistry must be a public GitHub Container Registry image path")
    public boolean isContainerRegistryValidForSourceType() {
        if (resolvedSourceType() != ProjectSourceType.CONTAINER_IMAGE) {
            return true;
        }

        return containerRegistry != null
                && !containerRegistry.isBlank()
                && PUBLIC_GITHUB_CONTAINER_REGISTRY.matcher(containerRegistry).matches();
    }

    @AssertTrue(message = "must be relative and stay inside the repository")
    public boolean isDockerfilePathSafe() {
        if (dockerfilePath == null || dockerfilePath.isBlank()) {
            return true;
        }

        String normalizedPath = dockerfilePath.replace('\\', '/');
        if (normalizedPath.startsWith("/") || normalizedPath.isBlank()) {
            return false;
        }

        for (String pathPart : normalizedPath.split("/")) {
            if (pathPart.equals("..")) {
                return false;
            }
        }

        return true;
    }

    @AssertTrue(message = "must start with /")
    public boolean isHealthCheckPathValid() {
        return healthCheckPath == null || healthCheckPath.isBlank() || healthCheckPath.startsWith("/");
    }

    private ProjectSourceType resolvedSourceType() {
        return sourceType != null ? sourceType : ProjectSourceType.GITHUB_REPOSITORY;
    }
}
