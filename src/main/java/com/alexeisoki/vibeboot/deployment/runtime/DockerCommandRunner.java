package com.alexeisoki.vibeboot.deployment.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class DockerCommandRunner {

    private static final int COMMAND_START_FAILED_EXIT_CODE = -1;

    public DockerCommandResult run(List<String> command, Duration timeout) {
        return run(command, null, timeout);
    }

    public DockerCommandResult run(List<String> command, Path workingDirectory, Duration timeout) {
        validate(command, timeout);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            return commandStartFailed(exception);
        }

        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                return new DockerCommandResult(
                        process.exitValue(),
                        readOutput(stdout),
                        readOutput(stderr),
                        true
                );
            }

            return new DockerCommandResult(
                    process.exitValue(),
                    readOutput(stdout),
                    readOutput(stderr),
                    false
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new DockerCommandResult(
                    COMMAND_START_FAILED_EXIT_CODE,
                    readOutput(stdout),
                    readOutput(stderr) + exception.getMessage(),
                    false
            );
        }
    }

    private void validate(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    private DockerCommandResult commandStartFailed(IOException exception) {
        return new DockerCommandResult(
                COMMAND_START_FAILED_EXIT_CODE,
                "",
                exception.getMessage(),
                false
        );
    }

    private CompletableFuture<String> readAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private String readOutput(CompletableFuture<String> output) {
        try {
            return output.join();
        } catch (CompletionException exception) {
            return exception.getMessage();
        }
    }
}
