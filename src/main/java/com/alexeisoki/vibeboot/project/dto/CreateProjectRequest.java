package com.alexeisoki.vibeboot.project.dto;
//record just gives us a simple immutable data class with a constructor, getters, equals, hashCode, and toString all generated for us.
public record CreateProjectRequest(
        String name,
        String repositoryUrl,
        String branch,
        String runCommand
) {
}
