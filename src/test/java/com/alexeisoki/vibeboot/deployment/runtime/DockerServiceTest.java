package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
    private DockerCommandRunner dockerCommandRunner;

    @Test
    void buildImage_runsDockerBuildAndReturnsImageName() {
        DockerService dockerService = new DockerService(dockerCommandRunner);
        Project project = project("Payment API");
        Path projectDirectory = Path.of("/tmp/sample-app");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(dockerCommandRunner.run(
                List.of("docker", "build", "-t", imageName, "-f", "Dockerfile", "."),
                projectDirectory,
                Duration.ofMinutes(5)
        )).thenReturn(new DockerCommandResult(0, "build ok", "", false));

        DockerBuildResult result = dockerService.buildImage(DEPLOYMENT_ID, project, projectDirectory);

        assertThat(result.imageName()).isEqualTo(imageName);
        assertThat(result.output()).isEqualTo("build ok");
    }

    @Test
    void buildImage_throwsWhenDockerBuildFails() {
        DockerService dockerService = new DockerService(dockerCommandRunner);
        Project project = project("Payment API");
        Path projectDirectory = Path.of("/tmp/sample-app");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(dockerCommandRunner.run(
                List.of("docker", "build", "-t", imageName, "-f", "Dockerfile", "."),
                projectDirectory,
                Duration.ofMinutes(5)
        )).thenReturn(new DockerCommandResult(1, "", "bad Dockerfile", false));

        assertThatThrownBy(() -> dockerService.buildImage(DEPLOYMENT_ID, project, projectDirectory))
                .isInstanceOf(DockerServiceException.class)
                .hasMessageContaining("Docker image build failed")
                .hasMessageContaining("bad Dockerfile");
    }

    @Test
    void runContainer_runsDetachedContainerAndReturnsRuntimeMetadata() {
        DockerService dockerService = new DockerService(dockerCommandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(dockerCommandRunner.run(
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
        )).thenReturn(new DockerCommandResult(0, "abc123\n", "", false));

        DockerRunResult result = dockerService.runContainer(DEPLOYMENT_ID, project, imageName, 49152);

        assertThat(result.containerId()).isEqualTo("abc123");
        assertThat(result.hostPort()).isEqualTo(49152);
        assertThat(result.containerPort()).isEqualTo(8080);
        assertThat(result.deploymentUrl()).isEqualTo("http://localhost:49152");
    }

    @Test
    void runContainer_throwsWhenDockerDoesNotReturnContainerId() {
        DockerService dockerService = new DockerService(dockerCommandRunner);
        Project project = project("Payment API");
        String imageName = "vibeboot-payment-api:" + DEPLOYMENT_ID;

        when(dockerCommandRunner.run(
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
        )).thenReturn(new DockerCommandResult(0, "   ", "", false));

        assertThatThrownBy(() -> dockerService.runContainer(DEPLOYMENT_ID, project, imageName, 49152))
                .isInstanceOf(DockerServiceException.class)
                .hasMessage("Docker container run failed: command succeeded but no container id was returned");
    }

    @Test
    void stopContainer_runsDockerStop() {
        DockerService dockerService = new DockerService(dockerCommandRunner);

        when(dockerCommandRunner.run(
                List.of("docker", "stop", "abc123"),
                Duration.ofSeconds(30)
        )).thenReturn(new DockerCommandResult(0, "abc123\n", "", false));

        dockerService.stopContainer("abc123");

        verify(dockerCommandRunner).run(
                List.of("docker", "stop", "abc123"),
                Duration.ofSeconds(30)
        );
    }

    @Test
    void getContainerLogs_returnsCombinedContainerLogs() {
        DockerService dockerService = new DockerService(dockerCommandRunner);

        when(dockerCommandRunner.run(
                List.of("docker", "logs", "abc123"),
                Duration.ofSeconds(30)
        )).thenReturn(new DockerCommandResult(0, "hello\n", "warn\n", false));

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
