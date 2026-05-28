package com.alexeisoki.vibeboot.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

//record just gives us a simple immutable data class with a constructor, getters, equals, hashCode, and toString all generated for us.
public record CreateProjectRequest(
        @NotBlank
        String name,
        @NotBlank
        String repositoryUrl,
        @NotBlank
        String branch,
        @NotBlank
        String runCommand,
        String dockerfilePath,
        @Positive
        Integer containerPort,
        String healthCheckPath
) {
    public CreateProjectRequest(String name, String repositoryUrl, String branch, String runCommand) {
        this(name, repositoryUrl, branch, runCommand, null, null, null);
    }
}
