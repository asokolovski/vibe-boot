package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class DeploymentRepositoryTest {

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Test
    void markRunningIfQueued_claimsDeploymentOnceAndIncrementsAttemptCount() {
        Deployment deployment = deploymentRepository.saveAndFlush(
                new Deployment(java.util.UUID.randomUUID())
        );
        Instant startedAt = Instant.parse("2026-07-30T12:00:00Z");

        int firstClaim = deploymentRepository.markRunningIfQueued(
                deployment.getId(),
                startedAt,
                DeploymentStatus.QUEUED,
                DeploymentStatus.RUNNING
        );
        int secondClaim = deploymentRepository.markRunningIfQueued(
                deployment.getId(),
                startedAt.plusSeconds(1),
                DeploymentStatus.QUEUED,
                DeploymentStatus.RUNNING
        );

        Deployment claimedDeployment = deploymentRepository.findById(deployment.getId()).orElseThrow();

        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
        assertThat(claimedDeployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(claimedDeployment.getStartedAt()).isEqualTo(startedAt);
        assertThat(claimedDeployment.getAttemptCount()).isEqualTo(1);
    }
}
