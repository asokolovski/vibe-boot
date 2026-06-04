package com.alexeisoki.vibeboot.deployment.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.project.Project;

@Service
public class DockerService {

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LOGS_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern ENV_VAR_KEY_PATTERN = Pattern.compile("[A-Z_][A-Z0-9_]*");

    private final CommandRunner commandRunner;

    public DockerService(CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    public DockerBuildResult buildImage(UUID deploymentId, Project project, Path projectDirectory) {
        validateDeploymentId(deploymentId);
        validateProject(project);
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }

        String imageName = toImageName(deploymentId, project);
        CommandResult result = commandRunner.run(
                List.of(
                        "docker",
                        "build",
                        "-t",
                        imageName,
                        "-f",
                        project.getDockerfilePath(),
                        "."
                ),
                projectDirectory,
                BUILD_TIMEOUT
        );

        requireSuccess(result, "Docker image build failed");
        return new DockerBuildResult(imageName, combineOutput(result.stdout(), result.stderr()));
    }

    public DockerRunResult runContainer(
            UUID deploymentId,
            Project project,
            String imageName,
            int hostPort,
            Map<String, String> environmentVariables
    ) {
        validateDeploymentId(deploymentId);
        validateProject(project);
        validateText(imageName, "imageName");
        if (hostPort <= 0) {
            throw new IllegalArgumentException("hostPort must be positive");
        }
        validateEnvironmentVariables(environmentVariables);

        int containerPort = project.getContainerPort();
        List<String> command = dockerRunCommand(
                deploymentId,
                imageName,
                hostPort,
                containerPort,
                environmentVariables
        );
        CommandResult result = commandRunner.run(
                command,
                RUN_TIMEOUT
        );

        requireSuccess(result, "Docker container run failed");

        String containerId = result.stdout().trim();
        if (containerId.isBlank()) {
            throw new DockerServiceException("Docker container run failed: command succeeded but no container id was returned");
        }

        return new DockerRunResult(
                containerId,
                hostPort,
                containerPort,
                "http://localhost:" + hostPort
        );
    }

    private List<String> dockerRunCommand(
            UUID deploymentId,
            String imageName,
            int hostPort,
            int containerPort,
            Map<String, String> environmentVariables
    ) {
        List<String> command = new ArrayList<>(List.of(
                "docker",
                "run",
                "-d",
                "--name",
                toContainerName(deploymentId),
                "-p",
                hostPort + ":" + containerPort
        ));

        environmentVariables.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    command.add("-e");
                    command.add(entry.getKey() + "=" + entry.getValue());
                });

        command.add(imageName);
        return command;
    }

    public void stopContainer(String containerId) {
        validateText(containerId, "containerId");

        CommandResult result = commandRunner.run(
                List.of("docker", "stop", containerId),
                STOP_TIMEOUT
        );

        requireSuccess(result, "Docker container stop failed");
    }

    public String getContainerLogs(String containerId) {
        validateText(containerId, "containerId");

        CommandResult result = commandRunner.run(
                List.of("docker", "logs", containerId),
                LOGS_TIMEOUT
        );

        requireSuccess(result, "Docker container logs failed");
        return combineOutput(result.stdout(), result.stderr());
    }

    private String toImageName(UUID deploymentId, Project project) {
        return "vibeboot-" + toDockerSafeNamePart(project.getName()) + ":" + deploymentId;
    }

    private String toContainerName(UUID deploymentId) {
        return "vibeboot-deployment-" + deploymentId;
    }

    private String toDockerSafeNamePart(String value) {
        String safeValue = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^[._-]+|[._-]+$", "");

        return safeValue.isBlank() ? "project" : safeValue;
    }

    private void requireSuccess(CommandResult result, String failureMessage) {
        if (result.succeeded()) {
            return;
        }

        throw new DockerServiceException(
                failureMessage + ": " + failureReason(result),
                combineOutput(result.stdout(), result.stderr())
        );
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

    private void validateDeploymentId(UUID deploymentId) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }
    }

    private void validateProject(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private void validateEnvironmentVariables(Map<String, String> environmentVariables) {
        if (environmentVariables == null) {
            throw new IllegalArgumentException("environmentVariables must not be null");
        }

        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            if (entry.getKey() == null || !ENV_VAR_KEY_PATTERN.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("environment variable key must match [A-Z_][A-Z0-9_]*");
            }

            if (entry.getValue() == null) {
                throw new IllegalArgumentException("environment variable value must not be null");
            }
        }
    }
}
