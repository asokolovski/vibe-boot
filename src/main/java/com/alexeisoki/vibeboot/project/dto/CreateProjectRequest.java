package com.alexeisoki.vibeboot.project.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

//record just gives us a simple immutable data class with a constructor, getters, equals, hashCode, and toString all generated for us.
public record CreateProjectRequest(
        @NotBlank
        String name,
        @NotBlank
        @Pattern(
                regexp = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$",
                message = "must be a public HTTPS GitHub repository URL"
        )
        String repositoryUrl,
        String branch,
        String runCommand,
        String localPath,
        String dockerfilePath,
        @Min(1)
        @Max(65535)
        Integer containerPort,
        String healthCheckPath
) {
    public CreateProjectRequest(String name, String repositoryUrl) {
        this(name, repositoryUrl, null, null, null, null, null, null);
    }

    public CreateProjectRequest(String name, String repositoryUrl, String branch, String runCommand, String localPath) {
        this(name, repositoryUrl, branch, runCommand, localPath, null, null, null);
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
}
