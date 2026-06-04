package com.alexeisoki.vibeboot.deployment.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class GitService {
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(2);
    private static final Pattern PUBLIC_GITHUB_REPOSITORY_URL = Pattern.compile(
            "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$"
    );

    private final CommandRunner commandRunner;

    public GitService(CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    public GitCloneResult cloneRepository(String repositoryUrl, String branch, Path targetDirectory) {
        validateRepositoryUrl(repositoryUrl);
        validateText(branch, "branch");
        if (targetDirectory == null) {
            throw new IllegalArgumentException("targetDirectory must not be null");
        }

        CommandResult result = commandRunner.run(
                List.of(
                        "git",
                        "clone",
                        "--depth",
                        "1",
                        "--branch",
                        branch,
                        "--single-branch",
                        repositoryUrl,
                        targetDirectory.toAbsolutePath().normalize().toString()
                ),
                CLONE_TIMEOUT
        );

        String output = combineOutput(result.stdout(), result.stderr());
        if (!result.succeeded()) {
            throw new GitServiceException(
                    "Git repository clone failed: " + failureReason(result),
                    output
            );
        }

        return new GitCloneResult(output);
    }

    private void validateRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || !PUBLIC_GITHUB_REPOSITORY_URL.matcher(repositoryUrl).matches()) {
            throw new IllegalArgumentException("repositoryUrl must be a public HTTPS GitHub repository URL");
        }
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private String failureReason(CommandResult result) {
        if (result.timedOut()) {
            return "command timed out";
        }

        String output = combineOutput(result.stderr(), result.stdout()).trim();
        if (!output.isBlank()) {
            return output;
        }

        return "exit code " + result.exitCode();
    }

    private String combineOutput(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }

        if (second == null || second.isBlank()) {
            return first;
        }

        if (first.endsWith("\n") || second.startsWith("\n")) {
            return first + second;
        }

        return first + System.lineSeparator() + second;
    }
}
