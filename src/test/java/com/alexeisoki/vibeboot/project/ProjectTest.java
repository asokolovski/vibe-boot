package com.alexeisoki.vibeboot.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectTest {

    @Test
    void constructor_usesDockerRuntimeDefaultsWhenFieldsAreMissing() {
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                null,
                "./gradlew bootRun"
        );

        assertThat(project.getBranch()).isEqualTo("main");
        assertThat(project.getDockerfilePath()).isEqualTo("Dockerfile");
        assertThat(project.getContainerPort()).isEqualTo(8080);
        assertThat(project.getHealthCheckPath()).isEqualTo("/health");
    }

    @Test
    void constructor_allowsMissingRunCommand() {
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                " "
        );

        assertThat(project.getRunCommand()).isNull();
    }

    @Test
    void constructor_storesDockerRuntimeFieldsWhenProvided() {
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "./gradlew bootRun",
                "/home/alexei/projects/sample-app",
                "apps/api/Dockerfile",
                3000,
                "/ready"
        );

        assertThat(project.getLocalPath()).isEqualTo("/home/alexei/projects/sample-app");
        assertThat(project.getDockerfilePath()).isEqualTo("apps/api/Dockerfile");
        assertThat(project.getContainerPort()).isEqualTo(3000);
        assertThat(project.getHealthCheckPath()).isEqualTo("/ready");
    }
}
