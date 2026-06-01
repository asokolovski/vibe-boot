package com.alexeisoki.vibeboot.deployment.runtime;

public class DockerServiceException extends RuntimeException {
    private final String commandOutput;

    public DockerServiceException(String message) {
        this(message, "");
    }

    public DockerServiceException(String message, String commandOutput) {
        super(message);
        this.commandOutput = commandOutput == null ? "" : commandOutput;
    }

    public String commandOutput() {
        return commandOutput;
    }
}
