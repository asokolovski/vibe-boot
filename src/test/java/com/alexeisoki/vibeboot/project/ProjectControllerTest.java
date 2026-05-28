package com.alexeisoki.vibeboot.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private DeploymentService deploymentService;

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
                "./gradlew bootRun",
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
                  "runCommand": "./gradlew bootRun"
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
                .andExpect(jsonPath("$.runCommand").value("./gradlew bootRun"))
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
                  "runCommand": ""
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "branch must not be blank, name must not be blank, repositoryUrl must not be blank, runCommand must not be blank"
                ));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class));
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
}
