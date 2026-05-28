package com.alexeisoki.vibeboot.deployment.runtime;

public record DockerCommandResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut
) {
    public boolean succeeded() {
        return exitCode == 0 && !timedOut;
    }
}
