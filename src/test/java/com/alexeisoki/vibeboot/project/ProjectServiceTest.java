package com.alexeisoki.vibeboot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void createProject_savesProjectAndReturnsResponse() {
        // Arrange
        ProjectService projectService = new ProjectService(projectRepository);
        CreateProjectRequest request = new CreateProjectRequest(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot"
        );
        UUID generatedId = UUID.randomUUID();
        Instant generatedCreatedAt = Instant.parse("2026-05-14T12:00:00Z");
        Project savedProject = projectWithGeneratedFields(
                generatedId,
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                null,
                null,
                null,
                generatedCreatedAt
        );

        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        // Act
        ProjectResponse response = projectService.createProject(request);

        // Assert
        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.name()).isEqualTo("Vibe Boot");
        assertThat(response.repositoryUrl()).isEqualTo("https://github.com/alexeisoki/vibe-boot");
        assertThat(response.branch()).isEqualTo("main");
        assertThat(response.runCommand()).isNull();
        assertThat(response.localPath()).isNull();
        assertThat(response.dockerfilePath()).isEqualTo("Dockerfile");
        assertThat(response.containerPort()).isEqualTo(8080);
        assertThat(response.healthCheckPath()).isEqualTo("/health");
        assertThat(response.createdAt()).isEqualTo(generatedCreatedAt);

        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void createProject_usesProvidedDockerRuntimeFields() {
        // Arrange
        ProjectService projectService = new ProjectService(projectRepository);
        CreateProjectRequest request = new CreateProjectRequest(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "./gradlew bootRun",
                "/home/alexei/projects/sample-app",
                "apps/api/Dockerfile",
                3000,
                "/ready"
        );
        UUID generatedId = UUID.randomUUID();
        Instant generatedCreatedAt = Instant.parse("2026-05-14T12:00:00Z");
        Project savedProject = projectWithGeneratedFields(
                generatedId,
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "./gradlew bootRun",
                "/home/alexei/projects/sample-app",
                "apps/api/Dockerfile",
                3000,
                "/ready",
                generatedCreatedAt
        );

        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        // Act
        ProjectResponse response = projectService.createProject(request);

        // Assert
        assertThat(response.localPath()).isEqualTo("/home/alexei/projects/sample-app");
        assertThat(response.dockerfilePath()).isEqualTo("apps/api/Dockerfile");
        assertThat(response.containerPort()).isEqualTo(3000);
        assertThat(response.healthCheckPath()).isEqualTo("/ready");

        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void createProject_allowsMissingRunCommand() {
        // Arrange
        ProjectService projectService = new ProjectService(projectRepository);
        CreateProjectRequest request = new CreateProjectRequest(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                null,
                "/home/alexei/projects/sample-app"
        );
        UUID generatedId = UUID.randomUUID();
        Instant generatedCreatedAt = Instant.parse("2026-05-14T12:00:00Z");
        Project savedProject = projectWithGeneratedFields(
                generatedId,
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                null,
                "/home/alexei/projects/sample-app",
                generatedCreatedAt
        );

        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        // Act
        ProjectResponse response = projectService.createProject(request);

        // Assert
        assertThat(response.runCommand()).isNull();
        assertThat(response.localPath()).isEqualTo("/home/alexei/projects/sample-app");
        assertThat(response.dockerfilePath()).isEqualTo("Dockerfile");

        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void getAllProjects_returnsProjectResponses() {
        // Arrange
        ProjectService projectService = new ProjectService(projectRepository);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant firstCreatedAt = Instant.parse("2026-05-14T12:00:00Z");
        Instant secondCreatedAt = Instant.parse("2026-05-14T13:00:00Z");
        Project firstProject = projectWithGeneratedFields(
                firstId,
                "First App",
                "https://github.com/example/first",
                "main",
                "npm start",
                "/home/alexei/projects/first",
                firstCreatedAt
        );
        Project secondProject = projectWithGeneratedFields(
                secondId,
                "Second App",
                "https://github.com/example/second",
                "develop",
                "./gradlew bootRun",
                "/home/alexei/projects/second",
                secondCreatedAt
        );

        when(projectRepository.findAll()).thenReturn(List.of(firstProject, secondProject));

        // Act
        List<ProjectResponse> responses = projectService.getAllProjects();

        // Assert
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(firstId);
        assertThat(responses.get(0).name()).isEqualTo("First App");
        assertThat(responses.get(0).repositoryUrl()).isEqualTo("https://github.com/example/first");
        assertThat(responses.get(0).branch()).isEqualTo("main");
        assertThat(responses.get(0).runCommand()).isEqualTo("npm start");
        assertThat(responses.get(0).localPath()).isEqualTo("/home/alexei/projects/first");
        assertThat(responses.get(0).dockerfilePath()).isEqualTo("Dockerfile");
        assertThat(responses.get(0).containerPort()).isEqualTo(8080);
        assertThat(responses.get(0).healthCheckPath()).isEqualTo("/health");
        assertThat(responses.get(0).createdAt()).isEqualTo(firstCreatedAt);
        assertThat(responses.get(1).id()).isEqualTo(secondId);
        assertThat(responses.get(1).name()).isEqualTo("Second App");
        assertThat(responses.get(1).repositoryUrl()).isEqualTo("https://github.com/example/second");
        assertThat(responses.get(1).branch()).isEqualTo("develop");
        assertThat(responses.get(1).runCommand()).isEqualTo("./gradlew bootRun");
        assertThat(responses.get(1).localPath()).isEqualTo("/home/alexei/projects/second");
        assertThat(responses.get(1).dockerfilePath()).isEqualTo("Dockerfile");
        assertThat(responses.get(1).containerPort()).isEqualTo(8080);
        assertThat(responses.get(1).healthCheckPath()).isEqualTo("/health");
        assertThat(responses.get(1).createdAt()).isEqualTo(secondCreatedAt);

        verify(projectRepository).findAll();
    }

    @Test
    void getProjectOrThrow_returnsProjectWhenFound() {
        // Arrange
        ProjectService projectService = new ProjectService(projectRepository);
        UUID projectId = UUID.randomUUID();
        Project project = new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                "./gradlew bootRun"
        );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Act
        Project result = projectService.getProjectOrThrow(projectId);

        // Assert
        assertThat(result).isSameAs(project);
        verify(projectRepository).findById(projectId);
    }

    @Test
    void getProjectOrThrow_throwsWhenProjectIsMissing() {
        // Arrange
        ProjectService projectService = new ProjectService(projectRepository);
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> projectService.getProjectOrThrow(projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found");

        verify(projectRepository).findById(projectId);
    }

    private static Project projectWithGeneratedFields(
            UUID id,
            String name,
            String repositoryUrl,
            String branch,
            String runCommand,
            String localPath,
            Instant createdAt
    ) {
        Project project = projectWithGeneratedFields(
                id,
                name,
                repositoryUrl,
                branch,
                runCommand,
                localPath,
                null,
                null,
                null,
                createdAt
        );
        return project;
    }

    private static Project projectWithGeneratedFields(
            UUID id,
            String name,
            String repositoryUrl,
            String branch,
            String runCommand,
            String localPath,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath,
            Instant createdAt
    ) {
        Project project = new Project(
                name,
                repositoryUrl,
                branch,
                runCommand,
                localPath,
                dockerfilePath,
                containerPort,
                healthCheckPath
        );
        ReflectionTestUtils.setField(project, "id", id);
        ReflectionTestUtils.setField(project, "createdAt", createdAt);
        return project;
    }
}
