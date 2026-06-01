package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeploymentTest {

    @Test
    void newDeployment_hasNoDockerRuntimeMetadata() {
        Deployment deployment = new Deployment(UUID.randomUUID());

        assertThat(deployment.getImageName()).isNull();
        assertThat(deployment.getContainerId()).isNull();
        assertThat(deployment.getHostPort()).isNull();
        assertThat(deployment.getContainerPort()).isNull();
        assertThat(deployment.getDeploymentUrl()).isNull();
    }

    @Test
    void recordDockerRuntime_storesDockerRuntimeMetadata() {
        Deployment deployment = new Deployment(UUID.randomUUID());

        deployment.recordDockerRuntime(
                "vibeboot-payment-api-dep123",
                "abc123",
                49152,
                8080,
                "http://localhost:49152"
        );

        assertThat(deployment.getImageName()).isEqualTo("vibeboot-payment-api-dep123");
        assertThat(deployment.getContainerId()).isEqualTo("abc123");
        assertThat(deployment.getHostPort()).isEqualTo(49152);
        assertThat(deployment.getContainerPort()).isEqualTo(8080);
        assertThat(deployment.getDeploymentUrl()).isEqualTo("http://localhost:49152");
    }

    @Test
    void markStopped_setsStoppedStatusWithoutChangingFinishedAt() {
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markFinished(DeploymentStatus.SUCCESS);
        var finishedAt = deployment.getFinishedAt();

        deployment.markStopped();

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.STOPPED);
        assertThat(deployment.getFinishedAt()).isEqualTo(finishedAt);
    }

    @Test
    void markStopped_leavesFinishedAtNullWhenDeploymentWasNotFinished() {
        Deployment deployment = new Deployment(UUID.randomUUID());

        deployment.markStopped();

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.STOPPED);
        assertThat(deployment.getFinishedAt()).isNull();
    }
}
