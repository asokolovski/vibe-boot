package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeploymentExecutorTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Test
    void execute_marksDeploymentRunningThenSuccess() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentExecutor deploymentExecutor = new DeploymentExecutor(
                deploymentRepository,
                () -> true,
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository).markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        );
        verify(deploymentRepository).save(deployment);
    }

    @Test
    void execute_marksDeploymentRunningThenFailed() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentExecutor deploymentExecutor = new DeploymentExecutor(
                deploymentRepository,
                () -> false,
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository).markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        );
        verify(deploymentRepository).save(deployment);
    }

    @Test
    void execute_skipsDeploymentThatIsAlreadyRunning() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markRunning();
        DeploymentExecutor deploymentExecutor = new DeploymentExecutor(
                deploymentRepository,
                () -> {
                    throw new AssertionError("Already running deployment should not execute");
                },
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository, never()).markRunningIfQueued(
                any(UUID.class),
                any(Instant.class),
                any(DeploymentStatus.class),
                any(DeploymentStatus.class)
        );
        verify(deploymentRepository, never()).save(any(Deployment.class));
    }

    @Test
    void execute_skipsDeploymentThatAlreadyFinished() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markFinished(DeploymentStatus.SUCCESS);
        DeploymentExecutor deploymentExecutor = new DeploymentExecutor(
                deploymentRepository,
                () -> {
                    throw new AssertionError("Finished deployment should not execute again");
                },
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository, never()).markRunningIfQueued(
                any(UUID.class),
                any(Instant.class),
                any(DeploymentStatus.class),
                any(DeploymentStatus.class)
        );
        verify(deploymentRepository, never()).save(any(Deployment.class));
    }

    @Test
    void execute_skipsWhenAnotherConsumerAlreadyStartedDeployment() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentExecutor deploymentExecutor = new DeploymentExecutor(
                deploymentRepository,
                () -> {
                    throw new AssertionError("Deployment should not execute after losing the start race");
                },
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(0);

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.QUEUED);

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository).markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        );
        verify(deploymentRepository, never()).save(any(Deployment.class));
    }

    @Test
    void execute_throwsWhenDeploymentIsMissing() {
        UUID deploymentId = UUID.randomUUID();
        DeploymentExecutor deploymentExecutor = new DeploymentExecutor(
                deploymentRepository,
                () -> true,
                Duration.ZERO
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentExecutor.execute(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).findById(deploymentId);
    }
}
