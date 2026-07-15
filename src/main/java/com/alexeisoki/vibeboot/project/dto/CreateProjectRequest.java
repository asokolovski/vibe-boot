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
        String healthCheckPath,
        String composeFilePath,
        String primaryServiceName
) {
    private static final Pattern PUBLIC_GITHUB_REPOSITORY_URL = Pattern.compile(
            "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$"
    );
    private static final Pattern PUBLIC_GITHUB_CONTAINER_REGISTRY = Pattern.compile(
            "^ghcr\\.io/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*$"
    );

    public CreateProjectRequest(String name, String repositoryUrl) {
        this(name, repositoryUrl, null, null, null, null, null, null, null, null);
    }

    public CreateProjectRequest(
            String name,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath
    ) {
        this(name, repositoryUrl, null, null, branch, dockerfilePath, containerPort, healthCheckPath, null, null);
    }

    @AssertTrue(message = "repositoryUrl must be a public HTTPS GitHub repository URL")
    public boolean isRepositoryUrlValidForSourceType() {
        if (resolvedSourceType() == ProjectSourceType.CONTAINER_IMAGE) {
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
        if (resolvedSourceType() == ProjectSourceType.DOCKER_COMPOSE) {
            return true;
        }

        if (dockerfilePath == null || dockerfilePath.isBlank()) {
            return true;
        }

        return isSafeRelativePath(dockerfilePath);
    }

    @AssertTrue(message = "composeFilePath must be a relative .yaml or .yml path inside the repository")
    public boolean isComposeFilePathSafe() {
        if (composeFilePath == null || composeFilePath.isBlank()) {
            return true;
        }

        String normalizedPath = composeFilePath.replace('\\', '/');
        return isSafeRelativePath(normalizedPath)
                && (normalizedPath.endsWith(".yaml") || normalizedPath.endsWith(".yml"));
    }

    @AssertTrue(message = "primaryServiceName is required for Docker Compose projects and must use letters, numbers, dots, underscores, or hyphens")
    public boolean isPrimaryServiceNameValidForSourceType() {
        if (resolvedSourceType() != ProjectSourceType.DOCKER_COMPOSE) {
            return true;
        }

        return primaryServiceName != null
                && primaryServiceName.matches("[A-Za-z0-9_.-]+");
    }

    @AssertTrue(message = "must start with /")
    public boolean isHealthCheckPathValid() {
        return healthCheckPath == null || healthCheckPath.isBlank() || healthCheckPath.startsWith("/");
    }

    private ProjectSourceType resolvedSourceType() {
        return sourceType != null ? sourceType : ProjectSourceType.GITHUB_REPOSITORY;
    }

    private boolean isSafeRelativePath(String path) {
        String normalizedPath = path.replace('\\', '/');
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
}
