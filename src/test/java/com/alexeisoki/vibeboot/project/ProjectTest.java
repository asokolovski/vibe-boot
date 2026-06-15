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
}
