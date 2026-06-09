package com.alexeisoki.vibeboot.deployment.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

/*
 * Process execution model:
 *
 * This class runs external CLI commands from inside our Spring Boot app.
 * The caller is usually a RabbitMQ listener thread:
 *
 *   Rabbit listener thread
 *     -> DeploymentExecutor
 *     -> DockerService or GitService
 *     -> CommandRunner
 *     -> ProcessBuilder.start()
 *
 * processBuilder.start() asks the OS to start a separate child process, such as:
 *
 *   docker build ...
 *   docker run ...
 *   docker stop ...
 *
 * That external command is NOT running inside the Rabbit listener thread. It is a
 * separate OS process. The Rabbit listener thread is only responsible for
 * starting it, waiting for it to finish, enforcing a timeout, and collecting
 * the result.
 *
 * The child process has stdout and stderr streams. In a normal terminal, those
 * streams would print directly to the terminal. With ProcessBuilder, Java gives
 * us pipes instead:
 *
 *   child stdout -> process.getInputStream()
 *   child stderr -> process.getErrorStream()
 *
 * These pipes have limited buffer space. If the child process writes a lot of
 * output and our Java process does not read it, the pipe can fill up. Once a
 * pipe fills up, the external process can block while trying to write more output.
 * If that happens while the Rabbit listener thread is waiting for the process
 * to finish, the whole command can appear to hang.
 *
 * That is why we start two async reader tasks immediately after starting the
 * process:
 *
 *   one task drains stdout
 *   one task drains stderr
 *
 * These tasks are Java threads inside the same Spring Boot JVM, not separate OS
 * child processes. CompletableFuture.supplyAsync(...) uses Java's default
 * ForkJoinPool.commonPool() unless we provide a custom Executor.
 *
 * So during command execution we have:
 *
 *   Rabbit listener thread:
 *     starts the external child process
 *     starts stdout/stderr reader tasks
 *     waits for process completion or timeout
 *
 *   async stdout reader:
 *     blocks on stdout.readAllBytes()
 *     keeps stdout drained until the process exits and closes the stream
 *
 *   async stderr reader:
 *     blocks on stderr.readAllBytes()
 *     keeps stderr drained until the process exits and closes the stream
 *
 *   external child process:
 *     performs the actual external work
 *     writes normal output to stdout
 *     writes errors/warnings to stderr
 *
 * We read stdout and stderr concurrently so neither pipe can fill up while the
 * process is running. After the process exits, the streams close, the async
 * readers complete, and we can collect the final stdout/stderr strings along
 * with the exit code.
 *
 * Important distinction:
 *
 *   ProcessBuilder.start() creates a separate OS process.
 *   CompletableFuture.supplyAsync(...) creates Java tasks/threads inside our JVM.
 *
 * The child process does the external work. The async Java tasks only drain
 * its output pipes so the child process can finish safely.
 */

@Service
public class CommandRunner {

    private static final int COMMAND_START_FAILED_EXIT_CODE = -1;

    public CommandResult run(List<String> command, Duration timeout) {
        return run(command, null, Map.of(), timeout);
    }

    public CommandResult run(List<String> command, Map<String, String> environment, Duration timeout) {
        return run(command, null, environment, timeout);
    }

    public CommandResult run(List<String> command, Path workingDirectory, Duration timeout) {
        return run(command, workingDirectory, Map.of(), timeout);
    }

    public CommandResult run(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            Duration timeout
    ) {
        validate(command, timeout);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        if (environment != null && !environment.isEmpty()) {
            processBuilder.environment().putAll(environment);
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
                return new CommandResult(
                        process.exitValue(),
                        readOutput(stdout),
                        readOutput(stderr),
                        true
                );
            }

            return new CommandResult(
                    process.exitValue(),
                    readOutput(stdout),
                    readOutput(stderr),
                    false
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(
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

    private CommandResult commandStartFailed(IOException exception) {
        return new CommandResult(
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
