package com.alexeisoki.vibeboot.deployment.runtime;

public class GitServiceException extends RuntimeException {
    private final String commandOutput;

    public GitServiceException(String message, String commandOutput) {
        super(message);
        this.commandOutput = commandOutput == null ? "" : commandOutput;
    }

    public String commandOutput() {
        return commandOutput;
    }
}
