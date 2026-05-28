package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeploymentLogTest {

    @Test
    void constructor_setsDeploymentIdAndMessage() {
        UUID deploymentId = UUID.randomUUID();

        DeploymentLog deploymentLog = new DeploymentLog(deploymentId, "Deployment queued");

        assertThat(deploymentLog.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(deploymentLog.getMessage()).isEqualTo("Deployment queued");
        assertThat(deploymentLog.getCreatedAt()).isNull();
    }

    @Test
    void setCreatedAt_setsTimestampBeforePersist() {
        DeploymentLog deploymentLog = new DeploymentLog(UUID.randomUUID(), "Deployment queued");

        deploymentLog.setCreatedAt();

        assertThat(deploymentLog.getCreatedAt()).isNotNull();
    }
}
