package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentLogResponse;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeploymentLogServiceTest {

    @Mock
    private DeploymentLogRepository deploymentLogRepository;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Test
    void appendLog_verifiesDeploymentExistsAndSavesLog() {
        DeploymentLogService deploymentLogService = new DeploymentLogService(
                deploymentLogRepository,
                deploymentRepository
        );
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.existsById(deploymentId)).thenReturn(true);

        deploymentLogService.appendLog(deploymentId, "Building Docker image");

        ArgumentCaptor<DeploymentLog> logCaptor = ArgumentCaptor.forClass(DeploymentLog.class);
        verify(deploymentLogRepository).save(logCaptor.capture());
        DeploymentLog logToSave = logCaptor.getValue();
        assertThat(logToSave.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(logToSave.getMessage()).isEqualTo("Building Docker image");

        InOrder inOrder = inOrder(deploymentRepository, deploymentLogRepository);
        inOrder.verify(deploymentRepository).existsById(deploymentId);
        inOrder.verify(deploymentLogRepository).save(any(DeploymentLog.class));
    }

    @Test
    void appendLog_throwsWhenDeploymentIsMissing() {
        DeploymentLogService deploymentLogService = new DeploymentLogService(
                deploymentLogRepository,
                deploymentRepository
        );
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.existsById(deploymentId)).thenReturn(false);

        assertThatThrownBy(() -> deploymentLogService.appendLog(deploymentId, "Building Docker image"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).existsById(deploymentId);
        verify(deploymentLogRepository, never()).save(any(DeploymentLog.class));
    }

    @Test
    void getLogs_verifiesDeploymentExistsAndReturnsLogResponses() {
        DeploymentLogService deploymentLogService = new DeploymentLogService(
                deploymentLogRepository,
                deploymentRepository
        );
        UUID deploymentId = UUID.randomUUID();
        Instant firstCreatedAt = Instant.parse("2026-05-25T19:10:00Z");
        Instant secondCreatedAt = Instant.parse("2026-05-25T19:10:03Z");
        DeploymentLog firstLog = deploymentLog(deploymentId, "Deployment queued", firstCreatedAt);
        DeploymentLog secondLog = deploymentLog(deploymentId, "Building Docker image", secondCreatedAt);

        when(deploymentRepository.existsById(deploymentId)).thenReturn(true);
        when(deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId))
                .thenReturn(List.of(firstLog, secondLog));

        List<DeploymentLogResponse> responses = deploymentLogService.getLogs(deploymentId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).message()).isEqualTo("Deployment queued");
        assertThat(responses.get(0).createdAt()).isEqualTo(firstCreatedAt);
        assertThat(responses.get(1).message()).isEqualTo("Building Docker image");
        assertThat(responses.get(1).createdAt()).isEqualTo(secondCreatedAt);

        InOrder inOrder = inOrder(deploymentRepository, deploymentLogRepository);
        inOrder.verify(deploymentRepository).existsById(deploymentId);
        inOrder.verify(deploymentLogRepository).findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
    }

    @Test
    void getLogs_returnsEmptyListWhenDeploymentHasNoLogs() {
        DeploymentLogService deploymentLogService = new DeploymentLogService(
                deploymentLogRepository,
                deploymentRepository
        );
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.existsById(deploymentId)).thenReturn(true);
        when(deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId))
                .thenReturn(List.of());

        List<DeploymentLogResponse> responses = deploymentLogService.getLogs(deploymentId);

        assertThat(responses).isEmpty();
        verify(deploymentRepository).existsById(deploymentId);
        verify(deploymentLogRepository).findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
    }

    @Test
    void getLogs_throwsWhenDeploymentIsMissing() {
        DeploymentLogService deploymentLogService = new DeploymentLogService(
                deploymentLogRepository,
                deploymentRepository
        );
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.existsById(deploymentId)).thenReturn(false);

        assertThatThrownBy(() -> deploymentLogService.getLogs(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).existsById(deploymentId);
        verify(deploymentLogRepository, never()).findByDeploymentIdOrderByCreatedAtAsc(any(UUID.class));
    }

    private static DeploymentLog deploymentLog(UUID deploymentId, String message, Instant createdAt) {
        DeploymentLog deploymentLog = new DeploymentLog(deploymentId, message);
        ReflectionTestUtils.setField(deploymentLog, "createdAt", createdAt);
        return deploymentLog;
    }
}
