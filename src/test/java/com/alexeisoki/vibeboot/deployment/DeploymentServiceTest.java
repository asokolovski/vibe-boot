package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectService;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectService projectService;

    @Test
    void triggerDeployment_verifiesProjectSavesDeploymentAndReturnsResponse() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(deploymentRepository, projectService);
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

        verify(projectService).getProjectOrThrow(projectId);

        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(deploymentCaptor.capture());
        Deployment deploymentToSave = deploymentCaptor.getValue();
        assertThat(deploymentToSave.getProjectId()).isEqualTo(projectId);
        assertThat(deploymentToSave.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
    }

    @Test
    void getDeploymentOrThrow_returnsDeploymentResponseWhenFound() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(deploymentRepository, projectService);
        UUID deploymentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-15T12:00:00Z");
        Deployment deployment = deploymentWithGeneratedFields(deploymentId, projectId, createdAt);

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

        verify(deploymentRepository).findById(deploymentId);
    }

    @Test
    void getDeploymentOrThrow_throwsWhenDeploymentIsMissing() {
        // Arrange
        DeploymentService deploymentService = new DeploymentService(deploymentRepository, projectService);
        UUID deploymentId = UUID.randomUUID();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> deploymentService.getDeploymentOrThrow(deploymentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).findById(deploymentId);
    }

    private static Deployment deploymentWithGeneratedFields(UUID id, UUID projectId, Instant createdAt) {
        Deployment deployment = new Deployment(projectId);
        ReflectionTestUtils.setField(deployment, "id", id);
        ReflectionTestUtils.setField(deployment, "createdAt", createdAt);
        return deployment;
    }
}
