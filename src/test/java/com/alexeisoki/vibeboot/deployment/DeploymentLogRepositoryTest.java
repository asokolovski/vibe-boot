package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
class DeploymentLogRepositoryTest {

    @Autowired
    private DeploymentLogRepository deploymentLogRepository;

    @Test
    void save_persistsDeploymentLogAndSetsCreatedAt() {
        UUID deploymentId = UUID.randomUUID();

        DeploymentLog savedLog = deploymentLogRepository.save(
                new DeploymentLog(deploymentId, "Deployment queued")
        );

        assertThat(savedLog.getId()).isNotNull();
        assertThat(savedLog.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(savedLog.getMessage()).isEqualTo("Deployment queued");
        assertThat(savedLog.getCreatedAt()).isNotNull();
    }

    @Test
    void findByDeploymentIdOrderByCreatedAtAsc_returnsLogsForDeploymentInTimelineOrder() {
        UUID deploymentId = UUID.randomUUID();
        UUID otherDeploymentId = UUID.randomUUID();
        DeploymentLog newestLog = deploymentLog(
                deploymentId,
                "Deployment succeeded",
                Instant.parse("2026-05-25T19:10:06Z")
        );
        DeploymentLog oldestLog = deploymentLog(
                deploymentId,
                "Deployment queued",
                Instant.parse("2026-05-25T19:10:00Z")
        );
        DeploymentLog otherDeploymentLog = deploymentLog(
                otherDeploymentId,
                "Other deployment queued",
                Instant.parse("2026-05-25T19:10:03Z")
        );

        deploymentLogRepository.saveAll(List.of(newestLog, otherDeploymentLog, oldestLog));

        List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);

        assertThat(logs)
                .extracting(DeploymentLog::getMessage)
                .containsExactly("Deployment queued", "Deployment succeeded");
    }

    private static DeploymentLog deploymentLog(UUID deploymentId, String message, Instant createdAt) {
        DeploymentLog deploymentLog = new DeploymentLog(deploymentId, message);
        ReflectionTestUtils.setField(deploymentLog, "createdAt", createdAt);
        return deploymentLog;
    }
}
