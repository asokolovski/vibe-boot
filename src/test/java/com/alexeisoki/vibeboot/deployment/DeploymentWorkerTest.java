package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeploymentWorkerTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Test
    void runDeployment_marksDeploymentRunningThenSuccess() {
        // Arrange
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentWorker deploymentWorker = new DeploymentWorker(
                deploymentRepository,
                () -> true,
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        deploymentWorker.runDeployment(deploymentId);

        // Assert
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository, times(2)).save(deployment);
    }

    @Test
    void runDeployment_marksDeploymentRunningThenFailed() {
        // Arrange
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentWorker deploymentWorker = new DeploymentWorker(
                deploymentRepository,
                () -> false,
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        deploymentWorker.runDeployment(deploymentId);

        // Assert
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository, times(2)).save(deployment);
    }

    @Test
    void runDeployment_throwsWhenDeploymentIsMissing() {
        // Arrange
        UUID deploymentId = UUID.randomUUID();
        DeploymentWorker deploymentWorker = new DeploymentWorker(
                deploymentRepository,
                () -> true,
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> deploymentWorker.runDeployment(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).findById(deploymentId);
    }
}
