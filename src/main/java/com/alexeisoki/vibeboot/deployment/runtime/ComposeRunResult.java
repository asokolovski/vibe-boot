package com.alexeisoki.vibeboot.deployment.runtime;

public record ComposeRunResult(
        String composeProjectName,
        String primaryServiceName,
        int hostPort,
        int containerPort,
        String deploymentUrl,
        String output
) {
}
