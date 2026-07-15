package com.alexeisoki.vibeboot.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProjectTest {

    @Test
    void constructor_usesDockerRuntimeDefaultsWhenFieldsAreMissing() {
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                null
        );

        assertThat(project.getBranch()).isEqualTo("main");
        assertThat(project.getDockerfilePath()).isEqualTo("Dockerfile");
        assertThat(project.getContainerPort()).isEqualTo(8080);
        assertThat(project.getHealthCheckPath()).isEqualTo("/health");
        assertThat(project.getOwnerUserId()).isNull();
    }

    @Test
    void constructor_storesDockerRuntimeFieldsWhenProvided() {
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "apps/api/Dockerfile",
                3000,
                "/ready"
        );

        assertThat(project.getDockerfilePath()).isEqualTo("apps/api/Dockerfile");
        assertThat(project.getContainerPort()).isEqualTo(3000);
        assertThat(project.getHealthCheckPath()).isEqualTo("/ready");
    }

    @Test
    void constructor_storesOwnerUserIdWhenProvided() {
        UUID ownerUserId = UUID.randomUUID();

        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                null,
                null,
                null,
                ownerUserId
        );

        assertThat(project.getOwnerUserId()).isEqualTo(ownerUserId);
    }

    @Test
    void constructor_allowsComposeProjectsToInferContainerPortLater() {
        Project project = new Project(
                "YT Clipper",
                "https://github.com/asokolovski/yt-clipper-mvp",
                "main",
                null,
                null,
                "/",
                null,
                ProjectSourceType.DOCKER_COMPOSE,
                null,
                "compose.yaml",
                "frontend"
        );

        assertThat(project.getSourceType()).isEqualTo(ProjectSourceType.DOCKER_COMPOSE);
        assertThat(project.getComposeFilePath()).isEqualTo("compose.yaml");
        assertThat(project.getPrimaryServiceName()).isEqualTo("frontend");
        assertThat(project.getContainerPort()).isNull();
        assertThat(project.getHealthCheckPath()).isEqualTo("/");
    }
}
