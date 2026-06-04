package com.alexeisoki.vibeboot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alexeisoki.vibeboot.project.dto.AddProjectEnvironmentVariableRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectEnvironmentVariableResponse;
import com.alexeisoki.vibeboot.shared.EncryptionService;
import com.alexeisoki.vibeboot.shared.ResourceConflictException;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProjectEnvironmentVariableServiceTest {

    @Mock
    private ProjectEnvironmentVariableRepository environmentVariableRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private EncryptionService encryptionService;

    @Test
    void addEnvVar_encryptsValueBeforeSavingAndReturnsMetadata() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-03T12:00:00Z");
        AddProjectEnvironmentVariableRequest request =
                new AddProjectEnvironmentVariableRequest("DB_PASSWORD", "secret");
        ProjectEnvironmentVariableService service = service();

        when(environmentVariableRepository.existsByProjectIdAndKey(projectId, "DB_PASSWORD")).thenReturn(false);
        when(encryptionService.encrypt("secret")).thenReturn("v1:ciphertext");
        when(environmentVariableRepository.save(any(ProjectEnvironmentVariable.class))).thenAnswer(invocation -> {
            ProjectEnvironmentVariable saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", envId);
            ReflectionTestUtils.setField(saved, "createdAt", createdAt);
            return saved;
        });

        // Act
        ProjectEnvironmentVariableResponse response = service.addEnvVar(projectId, request);

        // Assert
        assertThat(response.id()).isEqualTo(envId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.key()).isEqualTo("DB_PASSWORD");
        assertThat(response.createdAt()).isEqualTo(createdAt);

        ArgumentCaptor<ProjectEnvironmentVariable> captor =
                ArgumentCaptor.forClass(ProjectEnvironmentVariable.class);
        verify(environmentVariableRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(captor.getValue().getKey()).isEqualTo("DB_PASSWORD");
        assertThat(captor.getValue().getValueEncrypted()).isEqualTo("v1:ciphertext");
        assertThat(captor.getValue().getValueEncrypted()).isNotEqualTo("secret");
        verify(projectService).getProjectOrThrow(projectId);
        verify(encryptionService).encrypt("secret");
    }

    @Test
    void addEnvVar_rejectsDuplicateProjectKey() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        AddProjectEnvironmentVariableRequest request =
                new AddProjectEnvironmentVariableRequest("DB_PASSWORD", "secret");
        ProjectEnvironmentVariableService service = service();

        when(environmentVariableRepository.existsByProjectIdAndKey(projectId, "DB_PASSWORD")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> service.addEnvVar(projectId, request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Project environment variable key already exists");

        verify(projectService).getProjectOrThrow(projectId);
        verify(encryptionService, never()).encrypt(any(String.class));
        verify(environmentVariableRepository, never()).save(any(ProjectEnvironmentVariable.class));
    }

    @Test
    void addEnvVar_rejectsInvalidKey() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        AddProjectEnvironmentVariableRequest request =
                new AddProjectEnvironmentVariableRequest("db-password", "secret");
        ProjectEnvironmentVariableService service = service();

        // Act + Assert
        assertThatThrownBy(() -> service.addEnvVar(projectId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("key must match [A-Z_][A-Z0-9_]*");

        verify(projectService).getProjectOrThrow(projectId);
        verify(environmentVariableRepository, never()).existsByProjectIdAndKey(any(UUID.class), any(String.class));
        verify(encryptionService, never()).encrypt(any(String.class));
    }

    @Test
    void listEnvVars_returnsMetadataOnly() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-03T12:00:00Z");
        ProjectEnvironmentVariable environmentVariable =
                environmentVariable(projectId, envId, "DB_PASSWORD", "v1:ciphertext", createdAt);
        ProjectEnvironmentVariableService service = service();

        when(environmentVariableRepository.findByProjectIdOrderByCreatedAtAsc(projectId))
                .thenReturn(List.of(environmentVariable));

        // Act
        List<ProjectEnvironmentVariableResponse> responses = service.listEnvVars(projectId);

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(envId);
        assertThat(responses.get(0).projectId()).isEqualTo(projectId);
        assertThat(responses.get(0).key()).isEqualTo("DB_PASSWORD");
        assertThat(responses.get(0).createdAt()).isEqualTo(createdAt);
        verify(projectService).getProjectOrThrow(projectId);
        verify(encryptionService, never()).decrypt(any(String.class));
    }

    @Test
    void deleteEnvVar_deletesProjectScopedEnvironmentVariable() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        ProjectEnvironmentVariable environmentVariable =
                environmentVariable(projectId, envId, "DB_PASSWORD", "v1:ciphertext", Instant.now());
        ProjectEnvironmentVariableService service = service();

        when(environmentVariableRepository.findByIdAndProjectId(envId, projectId))
                .thenReturn(Optional.of(environmentVariable));

        // Act
        service.deleteEnvVar(projectId, envId);

        // Assert
        verify(projectService).getProjectOrThrow(projectId);
        verify(environmentVariableRepository).delete(environmentVariable);
    }

    @Test
    void deleteEnvVar_throwsWhenEnvironmentVariableIsMissing() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        ProjectEnvironmentVariableService service = service();

        when(environmentVariableRepository.findByIdAndProjectId(envId, projectId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.deleteEnvVar(projectId, envId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project environment variable not found");

        verify(projectService).getProjectOrThrow(projectId);
        verify(environmentVariableRepository, never()).delete(any(ProjectEnvironmentVariable.class));
    }

    @Test
    void getDecryptedEnvVarsForProject_returnsRuntimeSecretMap() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        ProjectEnvironmentVariable first =
                environmentVariable(projectId, UUID.randomUUID(), "DB_PASSWORD", "v1:first", Instant.now());
        ProjectEnvironmentVariable second =
                environmentVariable(projectId, UUID.randomUUID(), "API_KEY", "v1:second", Instant.now());
        ProjectEnvironmentVariableService service = service();

        when(environmentVariableRepository.findByProjectIdOrderByCreatedAtAsc(projectId))
                .thenReturn(List.of(first, second));
        when(encryptionService.decrypt("v1:first")).thenReturn("secret");
        when(encryptionService.decrypt("v1:second")).thenReturn("api-key");

        // Act
        Map<String, String> envVars = service.getDecryptedEnvVarsForProject(projectId);

        // Assert
        assertThat(envVars)
                .containsEntry("DB_PASSWORD", "secret")
                .containsEntry("API_KEY", "api-key");
        verify(projectService).getProjectOrThrow(projectId);
    }

    private ProjectEnvironmentVariableService service() {
        return new ProjectEnvironmentVariableService(
                environmentVariableRepository,
                projectService,
                encryptionService
        );
    }

    private static ProjectEnvironmentVariable environmentVariable(
            UUID projectId,
            UUID envId,
            String key,
            String valueEncrypted,
            Instant createdAt
    ) {
        ProjectEnvironmentVariable environmentVariable =
                new ProjectEnvironmentVariable(projectId, key, valueEncrypted);
        ReflectionTestUtils.setField(environmentVariable, "id", envId);
        ReflectionTestUtils.setField(environmentVariable, "createdAt", createdAt);
        return environmentVariable;
    }
}
