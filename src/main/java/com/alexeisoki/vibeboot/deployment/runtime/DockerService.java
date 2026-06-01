package com.alexeisoki.vibeboot.deployment.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.project.Project;

@Service
public class DockerService {

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LOGS_TIMEOUT = Duration.ofSeconds(30);

    private final DockerCommandRunner dockerCommandRunner;

    public DockerService(DockerCommandRunner dockerCommandRunner) {
        this.dockerCommandRunner = dockerCommandRunner;
    }

    public DockerBuildResult buildImage(UUID deploymentId, Project project, Path projectDirectory) {
        validateDeploymentId(deploymentId);
        validateProject(project);
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }

        String imageName = toImageName(deploymentId, project);
        DockerCommandResult result = dockerCommandRunner.run(
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

    public DockerRunResult runContainer(UUID deploymentId, Project project, String imageName, int hostPort) {
        validateDeploymentId(deploymentId);
        validateProject(project);
        validateText(imageName, "imageName");
        if (hostPort <= 0) {
            throw new IllegalArgumentException("hostPort must be positive");
        }

        int containerPort = project.getContainerPort();
        DockerCommandResult result = dockerCommandRunner.run(
                List.of(
                        "docker",
                        "run",
                        "-d",
                        "--name",
                        toContainerName(deploymentId),
                        "-p",
                        hostPort + ":" + containerPort,
                        imageName
                ),
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

    public void stopContainer(String containerId) {
        validateText(containerId, "containerId");

        DockerCommandResult result = dockerCommandRunner.run(
                List.of("docker", "stop", containerId),
                STOP_TIMEOUT
        );

        requireSuccess(result, "Docker container stop failed");
    }

    public String getContainerLogs(String containerId) {
        validateText(containerId, "containerId");

        DockerCommandResult result = dockerCommandRunner.run(
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

    private void requireSuccess(DockerCommandResult result, String failureMessage) {
        if (result.succeeded()) {
            return;
        }

        throw new DockerServiceException(
                failureMessage + ": " + failureReason(result),
                combineOutput(result.stdout(), result.stderr())
        );
    }

    private String failureReason(DockerCommandResult result) {
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
}
