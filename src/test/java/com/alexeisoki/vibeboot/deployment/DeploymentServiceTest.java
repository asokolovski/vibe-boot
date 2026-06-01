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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.deployment.queue.DeploymentQueuePublisher;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.shared.ResourceConflictException;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private DeploymentQueuePublisher deploymentQueuePublisher;

    @Mock
    private DockerService dockerService;

    @Mock
    private DeploymentLogService deploymentLogService;

    @Test
    void triggerDeployment_verifiesProjectSavesDeploymentAndReturnsResponse() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-15T12:00:00Z");
        TriggerDeploymentRequest request = new TriggerDeploymentRequest(projectId);
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "./gradlew bootRun"
        );
        Deployment savedDeployment = deploymentWithGeneratedFields(deploymentId, projectId, createdAt);

        when(projectService.getProjectOrThrow(projectId)).thenReturn(project);
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(savedDeployment);

        // Act
        DeploymentResponse response = deploymentService.triggerDeployment(request);

        // Assert
        assertThat(response.id()).isEqualTo(deploymentId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.status()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.startedAt()).isNull();
        assertThat(response.finishedAt()).isNull();
        assertThat(response.imageName()).isNull();
        assertThat(response.containerId()).isNull();
        assertThat(response.hostPort()).isNull();
        assertThat(response.containerPort()).isNull();
        assertThat(response.deploymentUrl()).isNull();

        verify(projectService).getProjectOrThrow(projectId);

        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(deploymentCaptor.capture());
        Deployment deploymentToSave = deploymentCaptor.getValue();
        assertThat(deploymentToSave.getProjectId()).isEqualTo(projectId);
        assertThat(deploymentToSave.getStatus()).isEqualTo(DeploymentStatus.QUEUED);

        InOrder inOrder = inOrder(deploymentRepository, deploymentQueuePublisher);
        inOrder.verify(deploymentRepository).save(any(Deployment.class));
        inOrder.verify(deploymentQueuePublisher).publishDeploymentRequested(deploymentId);
    }

    @Test
    void getDeploymentOrThrow_returnsDeploymentResponseWhenFound() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID deploymentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-15T12:00:00Z");
        Deployment deployment = deploymentWithGeneratedFields(deploymentId, projectId, createdAt);
        deployment.recordDockerRuntime(
                "vibeboot-payment-api-dep123",
                "abc123",
                49152,
                8080,
                "http://localhost:49152"
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        // Act
        DeploymentResponse response = deploymentService.getDeploymentOrThrow(deploymentId);

        // Assert
        assertThat(response.id()).isEqualTo(deploymentId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.status()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.startedAt()).isNull();
        assertThat(response.finishedAt()).isNull();
        assertThat(response.imageName()).isEqualTo("vibeboot-payment-api-dep123");
        assertThat(response.containerId()).isEqualTo("abc123");
        assertThat(response.hostPort()).isEqualTo(49152);
        assertThat(response.containerPort()).isEqualTo(8080);
        assertThat(response.deploymentUrl()).isEqualTo("http://localhost:49152");

        verify(deploymentRepository).findById(deploymentId);
    }

    @Test
    void getDeploymentOrThrow_throwsWhenDeploymentIsMissing() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> deploymentService.getDeploymentOrThrow(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).findById(deploymentId);
    }

    @Test
    void getDeploymentsForProject_verifiesProjectAndReturnsDeploymentResponses() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID projectId = UUID.randomUUID();
        UUID firstDeploymentId = UUID.randomUUID();
        UUID secondDeploymentId = UUID.randomUUID();
        Instant firstCreatedAt = Instant.parse("2026-05-15T13:00:00Z");
        Instant secondCreatedAt = Instant.parse("2026-05-15T12:00:00Z");
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "./gradlew bootRun"
        );
        Deployment firstDeployment = deploymentWithGeneratedFields(firstDeploymentId, projectId, firstCreatedAt);
        Deployment secondDeployment = deploymentWithGeneratedFields(secondDeploymentId, projectId, secondCreatedAt);

        when(projectService.getProjectOrThrow(projectId)).thenReturn(project);
        when(deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(firstDeployment, secondDeployment));

        // Act
        List<DeploymentResponse> responses = deploymentService.getDeploymentsForProject(projectId);

        // Assert
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(firstDeploymentId);
        assertThat(responses.get(0).projectId()).isEqualTo(projectId);
        assertThat(responses.get(0).status()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(responses.get(0).createdAt()).isEqualTo(firstCreatedAt);
        assertThat(responses.get(0).startedAt()).isNull();
        assertThat(responses.get(0).finishedAt()).isNull();
        assertThat(responses.get(0).imageName()).isNull();
        assertThat(responses.get(0).containerId()).isNull();
        assertThat(responses.get(0).hostPort()).isNull();
        assertThat(responses.get(0).containerPort()).isNull();
        assertThat(responses.get(0).deploymentUrl()).isNull();
        assertThat(responses.get(1).id()).isEqualTo(secondDeploymentId);
        assertThat(responses.get(1).projectId()).isEqualTo(projectId);
        assertThat(responses.get(1).status()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(responses.get(1).createdAt()).isEqualTo(secondCreatedAt);
        assertThat(responses.get(1).startedAt()).isNull();
        assertThat(responses.get(1).finishedAt()).isNull();
        assertThat(responses.get(1).imageName()).isNull();
        assertThat(responses.get(1).containerId()).isNull();
        assertThat(responses.get(1).hostPort()).isNull();
        assertThat(responses.get(1).containerPort()).isNull();
        assertThat(responses.get(1).deploymentUrl()).isNull();

        verify(projectService).getProjectOrThrow(projectId);
        verify(deploymentRepository).findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Test
    void stopDeployment_stopsContainerMarksStoppedWritesLogsAndReturnsResponse() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID deploymentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-15T12:00:00Z");
        Deployment deployment = deploymentWithGeneratedFields(deploymentId, projectId, createdAt);
        deployment.recordDockerRuntime(
                "vibeboot-payment-api:" + deploymentId,
                "abc123",
                49152,
                8080,
                "http://localhost:49152"
        );
        deployment.markFinished(DeploymentStatus.SUCCESS);
        Instant finishedAt = deployment.getFinishedAt();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DeploymentResponse response = deploymentService.stopDeployment(deploymentId);

        // Assert
        assertThat(response.id()).isEqualTo(deploymentId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.status()).isEqualTo(DeploymentStatus.STOPPED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.finishedAt()).isEqualTo(finishedAt);
        assertThat(response.imageName()).isEqualTo("vibeboot-payment-api:" + deploymentId);
        assertThat(response.containerId()).isEqualTo("abc123");
        assertThat(response.hostPort()).isEqualTo(49152);
        assertThat(response.containerPort()).isEqualTo(8080);
        assertThat(response.deploymentUrl()).isEqualTo("http://localhost:49152");

        InOrder inOrder = inOrder(dockerService, deploymentRepository, deploymentLogService);
        inOrder.verify(dockerService).stopContainer("abc123");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker container stopped: abc123");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment stopped");
    }

    @Test
    void stopDeployment_throwsWhenDeploymentIsMissing() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> deploymentService.stopDeployment(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(dockerService, never()).stopContainer(any());
        verify(deploymentRepository, never()).save(any(Deployment.class));
        verify(deploymentLogService, never()).appendLog(any(), any());
    }

    @Test
    void stopDeployment_throwsWhenDeploymentHasNoContainer() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = deploymentWithGeneratedFields(
                deploymentId,
                UUID.randomUUID(),
                Instant.parse("2026-05-15T12:00:00Z")
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        // Act + Assert
        assertThatThrownBy(() -> deploymentService.stopDeployment(deploymentId))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Deployment has no running container to stop");

        verify(dockerService, never()).stopContainer(any());
        verify(deploymentRepository, never()).save(any(Deployment.class));
        verify(deploymentLogService, never()).appendLog(any(), any());
    }

    @Test
    void stopDeployment_throwsWhenDeploymentIsAlreadyStopped() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(
                deploymentRepository,
                projectService,
                deploymentQueuePublisher,
                dockerService,
                deploymentLogService
        );
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = deploymentWithGeneratedFields(
                deploymentId,
                UUID.randomUUID(),
                Instant.parse("2026-05-15T12:00:00Z")
        );
        deployment.recordDockerRuntime(
                "vibeboot-payment-api:" + deploymentId,
                "abc123",
                49152,
                8080,
                "http://localhost:49152"
        );
        deployment.markStopped();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        // Act + Assert
        assertThatThrownBy(() -> deploymentService.stopDeployment(deploymentId))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Deployment is already stopped");

        verify(dockerService, never()).stopContainer(any());
        verify(deploymentRepository, never()).save(any(Deployment.class));
        verify(deploymentLogService, never()).appendLog(any(), any());
    }

    private static Deployment deploymentWithGeneratedFields(UUID id, UUID projectId, Instant createdAt) {
        Deployment deployment = new Deployment(projectId);
        ReflectionTestUtils.setField(deployment, "id", id);
        ReflectionTestUtils.setField(deployment, "createdAt", createdAt);
        return deployment;
    }
}
