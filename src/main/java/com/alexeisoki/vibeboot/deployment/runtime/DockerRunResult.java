package com.alexeisoki.vibeboot.deployment.runtime;

public record DockerRunResult(
        String containerId,
        int hostPort,
        int containerPort,
        String deploymentUrl
) {
}
