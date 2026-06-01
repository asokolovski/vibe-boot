package com.alexeisoki.vibeboot.deployment.runtime;

public record DockerBuildResult(
        String imageName,
        String output
) {
}
