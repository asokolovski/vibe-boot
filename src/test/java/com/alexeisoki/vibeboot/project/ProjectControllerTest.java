package com.alexeisoki.vibeboot.project;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.alexeisoki.vibeboot.deployment.DeploymentService;
import com.alexeisoki.vibeboot.deployment.DeploymentStatus;
import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.project.dto.AddProjectEnvironmentVariableRequest;
import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectEnvironmentVariableResponse;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;
import com.alexeisoki.vibeboot.shared.ResourceConflictException;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private DeploymentService deploymentService;

    @MockitoBean
    private ProjectEnvironmentVariableService environmentVariableService;

    @Test
    void createProject_returnsCreatedAndResponseJson() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-14T12:00:00Z");
        ProjectResponse response = new ProjectResponse(
                projectId,
                "vibe-payment-api",
                "https://github.com/alexeisoko/payment-api",
                "main",
                null,
                null,
                "Dockerfile",
                8080,
                "/health",
                createdAt
        );
        String requestJson = """
                {
                  "name": "vibe-payment-api",
                  "repositoryUrl": "https://github.com/alexeisoko/payment-api"
                }
                """;

        when(projectService.createProject(any(CreateProjectRequest.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("vibe-payment-api"))
                .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/alexeisoko/payment-api"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.runCommand").doesNotExist())
                .andExpect(jsonPath("$.localPath").doesNotExist())
                .andExpect(jsonPath("$.dockerfilePath").value("Dockerfile"))
                .andExpect(jsonPath("$.containerPort").value(8080))
                .andExpect(jsonPath("$.healthCheckPath").value("/health"))
                .andExpect(jsonPath("$.createdAt").value("2026-05-14T12:00:00Z"));

        verify(projectService, times(1)).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void createProject_returnsBadRequestWhenFieldsAreBlank() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "name": "",
                  "repositoryUrl": "",
                  "branch": "",
                  "localPath": "",
                  "runCommand": ""
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name must not be blank")))
                .andExpect(jsonPath("$.message", containsString("repositoryUrl must not be blank")));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void createProject_returnsBadRequestWhenRepositoryUrlIsNotPublicGithubHttps() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "name": "vibe-payment-api",
                  "repositoryUrl": "git@github.com:alexeisoko/payment-api.git"
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("repositoryUrl must be a public HTTPS GitHub repository URL")));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void createProject_returnsBadRequestWhenDockerfilePathEscapesRepository() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "name": "vibe-payment-api",
                  "repositoryUrl": "https://github.com/alexeisoko/payment-api",
                  "dockerfilePath": "../Dockerfile"
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be relative and stay inside the repository")));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void createProject_returnsBadRequestWhenContainerPortIsOutOfRange() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "name": "vibe-payment-api",
                  "repositoryUrl": "https://github.com/alexeisoko/payment-api",
                  "containerPort": 70000
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("containerPort must be less than or equal to 65535")));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void createProject_returnsBadRequestWhenHealthCheckPathDoesNotStartWithSlash() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "name": "vibe-payment-api",
                  "repositoryUrl": "https://github.com/alexeisoko/payment-api",
                  "healthCheckPath": "health"
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must start with /")));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void createProject_allowsMissingRunCommand() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-14T12:00:00Z");
        ProjectResponse response = new ProjectResponse(
                projectId,
                "vibe-payment-api",
                "https://github.com/alexeisoko/payment-api",
                "main",
                null,
                "/home/alexei/projects/sample-app",
                "Dockerfile",
                8080,
                "/health",
                createdAt
        );
        String requestJson = """
                {
                  "name": "vibe-payment-api",
                  "repositoryUrl": "https://github.com/alexeisoko/payment-api",
                  "branch": "main",
                  "localPath": "/home/alexei/projects/sample-app"
                }
                """;

        when(projectService.createProject(any(CreateProjectRequest.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.runCommand").doesNotExist());

        verify(projectService, times(1)).createProject(any(CreateProjectRequest.class));
    }

    @Test
    void getAllProjects_returnsOkAndProjectListJson() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-14T12:00:00Z");
        ProjectResponse response = new ProjectResponse(
                projectId,
                "vibe-payment-api",
                "https://github.com/alexeisoko/payment-api",
                "main",
                "./gradlew bootRun",
                "/home/alexei/projects/sample-app",
                "Dockerfile",
                8080,
                "/health",
                createdAt
        );

        when(projectService.getAllProjects()).thenReturn(List.of(response));

        // Act + Assert
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$[0].name").value("vibe-payment-api"))
                .andExpect(jsonPath("$[0].repositoryUrl").value("https://github.com/alexeisoko/payment-api"))
                .andExpect(jsonPath("$[0].branch").value("main"))
                .andExpect(jsonPath("$[0].runCommand").value("./gradlew bootRun"))
                .andExpect(jsonPath("$[0].localPath").value("/home/alexei/projects/sample-app"))
                .andExpect(jsonPath("$[0].dockerfilePath").value("Dockerfile"))
                .andExpect(jsonPath("$[0].containerPort").value(8080))
                .andExpect(jsonPath("$[0].healthCheckPath").value("/health"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-05-14T12:00:00Z"));

        verify(projectService, times(1)).getAllProjects();
    }

    @Test
    void getDeploymentsForProject_returnsOkAndDeploymentListJson() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-15T12:00:00Z");
        DeploymentResponse response = new DeploymentResponse(
                deploymentId,
                projectId,
                DeploymentStatus.QUEUED,
                createdAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(deploymentService.getDeploymentsForProject(projectId)).thenReturn(List.of(response));

        // Act + Assert
        mockMvc.perform(get("/api/projects/{projectId}/deployments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deploymentId.toString()))
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].status").value("QUEUED"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-05-15T12:00:00Z"))
                .andExpect(jsonPath("$[0].startedAt").doesNotExist())
                .andExpect(jsonPath("$[0].finishedAt").doesNotExist())
                .andExpect(jsonPath("$[0].imageName").doesNotExist())
                .andExpect(jsonPath("$[0].containerId").doesNotExist())
                .andExpect(jsonPath("$[0].hostPort").doesNotExist())
                .andExpect(jsonPath("$[0].containerPort").doesNotExist())
                .andExpect(jsonPath("$[0].deploymentUrl").doesNotExist());

        verify(deploymentService, times(1)).getDeploymentsForProject(projectId);
    }

    @Test
    void getDeploymentsForProject_returnsNotFoundWhenProjectIsMissing() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();

        when(deploymentService.getDeploymentsForProject(projectId))
                .thenThrow(new ResourceNotFoundException("Project not found"));

        // Act + Assert
        mockMvc.perform(get("/api/projects/{projectId}/deployments", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));

        verify(deploymentService, times(1)).getDeploymentsForProject(projectId);
    }

    @Test
    void addEnvVar_returnsCreatedMetadataWithoutSecretValues() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-03T12:00:00Z");
        ProjectEnvironmentVariableResponse response = new ProjectEnvironmentVariableResponse(
                envId,
                projectId,
                "DB_PASSWORD",
                createdAt
        );
        String requestJson = """
                {
                  "key": "DB_PASSWORD",
                  "value": "secret"
                }
                """;

        when(environmentVariableService.addEnvVar(any(UUID.class), any(AddProjectEnvironmentVariableRequest.class)))
                .thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/projects/{projectId}/env", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(envId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.key").value("DB_PASSWORD"))
                .andExpect(jsonPath("$.createdAt").value("2026-06-03T12:00:00Z"))
                .andExpect(jsonPath("$.value").doesNotExist())
                .andExpect(jsonPath("$.valueEncrypted").doesNotExist());

        verify(environmentVariableService).addEnvVar(any(UUID.class), any(AddProjectEnvironmentVariableRequest.class));
    }

    @Test
    void addEnvVar_returnsBadRequestWhenKeyIsInvalid() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        String requestJson = """
                {
                  "key": "db-password",
                  "value": "secret"
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects/{projectId}/env", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("key must match [A-Z_][A-Z0-9_]*")));

        verify(environmentVariableService, never())
                .addEnvVar(any(UUID.class), any(AddProjectEnvironmentVariableRequest.class));
    }

    @Test
    void addEnvVar_returnsConflictWhenKeyAlreadyExists() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        String requestJson = """
                {
                  "key": "DB_PASSWORD",
                  "value": "secret"
                }
                """;

        when(environmentVariableService.addEnvVar(any(UUID.class), any(AddProjectEnvironmentVariableRequest.class)))
                .thenThrow(new ResourceConflictException("Project environment variable key already exists"));

        // Act + Assert
        mockMvc.perform(post("/api/projects/{projectId}/env", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Project environment variable key already exists"));
    }

    @Test
    void listEnvVars_returnsMetadataWithoutSecretValues() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-03T12:00:00Z");
        ProjectEnvironmentVariableResponse response = new ProjectEnvironmentVariableResponse(
                envId,
                projectId,
                "DB_PASSWORD",
                createdAt
        );

        when(environmentVariableService.listEnvVars(projectId)).thenReturn(List.of(response));

        // Act + Assert
        mockMvc.perform(get("/api/projects/{projectId}/env", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(envId.toString()))
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].key").value("DB_PASSWORD"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-03T12:00:00Z"))
                .andExpect(jsonPath("$[0].value").doesNotExist())
                .andExpect(jsonPath("$[0].valueEncrypted").doesNotExist());

        verify(environmentVariableService).listEnvVars(projectId);
    }

    @Test
    void deleteEnvVar_returnsNoContent() throws Exception {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();

        // Act + Assert
        mockMvc.perform(delete("/api/projects/{projectId}/env/{envId}", projectId, envId))
                .andExpect(status().isNoContent());

        verify(environmentVariableService).deleteEnvVar(projectId, envId);
    }
}
