package com.alexeisoki.vibeboot.deployment;

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

import com.alexeisoki.vibeboot.deployment.dto.DeploymentLogResponse;
import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@WebMvcTest(DeploymentController.class)
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentService deploymentService;

    @MockitoBean
    private DeploymentLogService deploymentLogService;

    @Test
    void triggerDeployment_returnsCreatedAndResponseJson() throws Exception {
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
                "vibeboot-payment-api-dep123",
                "abc123",
                49152,
                8080,
                "http://localhost:49152"
        );
        String requestJson = """
                {
                  "projectId": "%s"
                }
                """.formatted(projectId);

        when(deploymentService.triggerDeployment(any(TriggerDeploymentRequest.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(deploymentId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.createdAt").value("2026-05-15T12:00:00Z"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.imageName").value("vibeboot-payment-api-dep123"))
                .andExpect(jsonPath("$.containerId").value("abc123"))
                .andExpect(jsonPath("$.hostPort").value(49152))
                .andExpect(jsonPath("$.containerPort").value(8080))
                .andExpect(jsonPath("$.deploymentUrl").value("http://localhost:49152"));

        verify(deploymentService, times(1)).triggerDeployment(any(TriggerDeploymentRequest.class));
    }

    @Test
    void triggerDeployment_returnsBadRequestWhenProjectIdIsNull() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "projectId": null
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("projectId must not be null"));

        verify(deploymentService, never()).triggerDeployment(any(TriggerDeploymentRequest.class));
    }

    @Test
    void triggerDeployment_returnsNotFoundWhenProjectIsMissing() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "projectId": "00000000-0000-0000-0000-000000000000"
                }
                """;

        when(deploymentService.triggerDeployment(any(TriggerDeploymentRequest.class)))
                .thenThrow(new ResourceNotFoundException("Project not found"));

        // Act + Assert
        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));

        verify(deploymentService, times(1)).triggerDeployment(any(TriggerDeploymentRequest.class));
    }

    @Test
    void getDeployment_returnsOkAndResponseJson() throws Exception {
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

        when(deploymentService.getDeploymentOrThrow(deploymentId)).thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/deployments/{deploymentId}", deploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deploymentId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.createdAt").value("2026-05-15T12:00:00Z"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.imageName").doesNotExist())
                .andExpect(jsonPath("$.containerId").doesNotExist())
                .andExpect(jsonPath("$.hostPort").doesNotExist())
                .andExpect(jsonPath("$.containerPort").doesNotExist())
                .andExpect(jsonPath("$.deploymentUrl").doesNotExist());

        verify(deploymentService, times(1)).getDeploymentOrThrow(deploymentId);
    }

    @Test
    void getDeployment_returnsNotFoundWhenDeploymentIsMissing() throws Exception {
        // Arrange
        UUID deploymentId = UUID.randomUUID();

        when(deploymentService.getDeploymentOrThrow(deploymentId))
                .thenThrow(new ResourceNotFoundException("Deployment not found"));

        // Act + Assert
        mockMvc.perform(get("/api/deployments/{deploymentId}", deploymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Deployment not found"));

        verify(deploymentService, times(1)).getDeploymentOrThrow(deploymentId);
    }

    @Test
    void getDeployment_returnsBadRequestWhenDeploymentIdIsInvalid() throws Exception {
        // Act + Assert
        mockMvc.perform(get("/api/deployments/fake-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("deploymentId must be a valid UUID"));
    }

    @Test
    void getDeploymentLogs_returnsOkAndResponseJson() throws Exception {
        // Arrange
        UUID deploymentId = UUID.randomUUID();
        Instant firstCreatedAt = Instant.parse("2026-05-25T19:10:00Z");
        Instant secondCreatedAt = Instant.parse("2026-05-25T19:10:03Z");

        when(deploymentLogService.getLogs(deploymentId)).thenReturn(List.of(
                new DeploymentLogResponse("Deployment queued", firstCreatedAt),
                new DeploymentLogResponse("Building Docker image", secondCreatedAt)
        ));

        // Act + Assert
        mockMvc.perform(get("/api/deployments/{deploymentId}/logs", deploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Deployment queued"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-05-25T19:10:00Z"))
                .andExpect(jsonPath("$[1].message").value("Building Docker image"))
                .andExpect(jsonPath("$[1].createdAt").value("2026-05-25T19:10:03Z"));

        verify(deploymentLogService, times(1)).getLogs(deploymentId);
    }

    @Test
    void getDeploymentLogs_returnsNotFoundWhenDeploymentIsMissing() throws Exception {
        // Arrange
        UUID deploymentId = UUID.randomUUID();

        when(deploymentLogService.getLogs(deploymentId))
                .thenThrow(new ResourceNotFoundException("Deployment not found"));

        // Act + Assert
        mockMvc.perform(get("/api/deployments/{deploymentId}/logs", deploymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Deployment not found"));

        verify(deploymentLogService, times(1)).getLogs(deploymentId);
    }

    @Test
    void getDeploymentLogs_returnsEmptyListWhenDeploymentHasNoLogs() throws Exception {
        // Arrange
        UUID deploymentId = UUID.randomUUID();

        when(deploymentLogService.getLogs(deploymentId)).thenReturn(List.of());

        // Act + Assert
        mockMvc.perform(get("/api/deployments/{deploymentId}/logs", deploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(deploymentLogService, times(1)).getLogs(deploymentId);
    }

    @Test
    void getDeploymentLogs_returnsBadRequestWhenDeploymentIdIsInvalid() throws Exception {
        // Act + Assert
        mockMvc.perform(get("/api/deployments/fake-id/logs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("deploymentId must be a valid UUID"));
    }
}
