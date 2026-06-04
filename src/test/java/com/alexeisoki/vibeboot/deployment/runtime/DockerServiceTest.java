package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexeisoki.vibeboot.project.Project;

@ExtendWith(MockitoExtension.class)
class DockerServiceTest {

    private static final UUID DEPLOYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Mock
    private CommandRunner commandRunner;

    @Test
    void buildImage_runsDockerBuildAndReturnsImageName() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        Path projectDirectory = Path.of("/tmp/sample-app");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(commandRunner.run(
                List.of("docker", "build", "-t", imageName, "-f", "Dockerfile", "."),
                projectDirectory,
                Duration.ofMinutes(5)
        )).thenReturn(new CommandResult(0, "build ok", "", false));

        DockerBuildResult result = dockerService.buildImage(DEPLOYMENT_ID, project, projectDirectory);

        assertThat(result.imageName()).isEqualTo(imageName);
        assertThat(result.output()).isEqualTo("build ok");
    }

    @Test
    void buildImage_throwsWhenDockerBuildFails() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        Path projectDirectory = Path.of("/tmp/sample-app");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(commandRunner.run(
                List.of("docker", "build", "-t", imageName, "-f", "Dockerfile", "."),
                projectDirectory,
                Duration.ofMinutes(5)
        )).thenReturn(new CommandResult(1, "", "bad Dockerfile", false));

        assertThatThrownBy(() -> dockerService.buildImage(DEPLOYMENT_ID, project, projectDirectory))
                .isInstanceOf(DockerServiceException.class)
                .hasMessageContaining("Docker image build failed")
                .hasMessageContaining("bad Dockerfile");
    }

    @Test
    void runContainer_runsDetachedContainerAndReturnsRuntimeMetadata() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(commandRunner.run(
                List.of(
                        "docker",
                        "run",
                        "-d",
                        "--name",
                        "vibeboot-deployment-" + DEPLOYMENT_ID,
                        "-p",
                        "49152:8080",
                        imageName
                ),
                Duration.ofSeconds(30)
        )).thenReturn(new CommandResult(0, "abc123\n", "", false));

        DockerRunResult result = dockerService.runContainer(DEPLOYMENT_ID, project, imageName, 49152, Map.of());

        assertThat(result.containerId()).isEqualTo("abc123");
        assertThat(result.hostPort()).isEqualTo(49152);
        assertThat(result.containerPort()).isEqualTo(8080);
        assertThat(result.deploymentUrl()).isEqualTo("http://localhost:49152");
    }

    @Test
    void runContainer_throwsWhenDockerDoesNotReturnContainerId() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(commandRunner.run(
                List.of(
                        "docker",
                        "run",
                        "-d",
                        "--name",
                        "vibeboot-deployment-" + DEPLOYMENT_ID,
                        "-p",
                        "49152:8080",
                        imageName
                ),
                Duration.ofSeconds(30)
        )).thenReturn(new CommandResult(0, "   ", "", false));

        assertThatThrownBy(() -> dockerService.runContainer(DEPLOYMENT_ID, project, imageName, 49152, Map.of()))
                .isInstanceOf(DockerServiceException.class)
                .hasMessage("Docker container run failed: command succeeded but no container id was returned");
    }

    @Test
    void runContainer_injectsEnvironmentVariablesBeforeImageName() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;
        Map<String, String> environmentVariables = new LinkedHashMap<>();
        environmentVariables.put("NODE_ENV", "production");
        environmentVariables.put("API_KEY", "secret");

        when(commandRunner.run(
                List.of(
                        "docker",
                        "run",
                        "-d",
                        "--name",
                        "vibeboot-deployment-" + DEPLOYMENT_ID,
                        "-p",
                        "49152:8080",
                        "-e",
                        "API_KEY=secret",
                        "-e",
                        "NODE_ENV=production",
                        imageName
                ),
                Duration.ofSeconds(30)
        )).thenReturn(new CommandResult(0, "abc123\n", "", false));

        DockerRunResult result = dockerService.runContainer(
                DEPLOYMENT_ID,
                project,
                imageName,
                49152,
                environmentVariables
        );

        assertThat(result.containerId()).isEqualTo("abc123");
    }

    @Test
    void runContainer_allowsEmptyEnvironmentVariableValue() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(commandRunner.run(
                List.of(
                        "docker",
                        "run",
                        "-d",
                        "--name",
                        "vibeboot-deployment-" + DEPLOYMENT_ID,
                        "-p",
                        "49152:8080",
                        "-e",
                        "OPTIONAL_VALUE=",
                        imageName
                ),
                Duration.ofSeconds(30)
        )).thenReturn(new CommandResult(0, "abc123\n", "", false));

        dockerService.runContainer(
                DEPLOYMENT_ID,
                project,
                imageName,
                49152,
                Map.of("OPTIONAL_VALUE", "")
        );

        verify(commandRunner).run(
                List.of(
                        "docker",
                        "run",
                        "-d",
                        "--name",
                        "vibeboot-deployment-" + DEPLOYMENT_ID,
                        "-p",
                        "49152:8080",
                        "-e",
                        "OPTIONAL_VALUE=",
                        imageName
                ),
                Duration.ofSeconds(30)
        );
    }

    @Test
    void runContainer_rejectsInvalidEnvironmentVariableKey() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        assertThatThrownBy(() -> dockerService.runContainer(
                DEPLOYMENT_ID,
                project,
                imageName,
                49152,
                Map.of("invalid-key", "value")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("environment variable key must match [A-Z_][A-Z0-9_]*");
    }

    @Test
    void runContainer_rejectsMissingEnvironmentVariableMap() {
        DockerService dockerService = new DockerService(commandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        assertThatThrownBy(() -> dockerService.runContainer(
                DEPLOYMENT_ID,
                project,
                imageName,
                49152,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("environmentVariables must not be null");
    }

    @Test
    void stopContainer_runsDockerStop() {
        DockerService dockerService = new DockerService(commandRunner);

        when(commandRunner.run(
                List.of("docker", "stop", "abc123"),
                Duration.ofSeconds(30)
        )).thenReturn(new CommandResult(0, "abc123\n", "", false));

        dockerService.stopContainer("abc123");

        verify(commandRunner).run(
                List.of("docker", "stop", "abc123"),
                Duration.ofSeconds(30)
        );
    }

    @Test
    void getContainerLogs_returnsCombinedContainerLogs() {
        DockerService dockerService = new DockerService(commandRunner);

        when(commandRunner.run(
                List.of("docker", "logs", "abc123"),
                Duration.ofSeconds(30)
        )).thenReturn(new CommandResult(0, "hello\n", "warn\n", false));

        String logs = dockerService.getContainerLogs("abc123");

        assertThat(logs).isEqualTo("hello\nwarn\n");
    }

    private Project project(String name) {
        return new Project(
                name,
                "https://github.com/alexeisoki/sample-app",
                "main",
                "npm start",
                "Dockerfile",
                8080,
                "/health"
        );
    }
}
