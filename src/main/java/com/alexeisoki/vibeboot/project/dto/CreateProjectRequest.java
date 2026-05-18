package com.alexeisoki.vibeboot.project.dto;

import jakarta.validation.constraints.NotBlank;

//record just gives us a simple immutable data class with a constructor, getters, equals, hashCode, and toString all generated for us.
public record CreateProjectRequest(
        @NotBlank
        String name,
        @NotBlank
        String repositoryUrl,
        @NotBlank
        String branch,
        @NotBlank
        String runCommand
) {
}
