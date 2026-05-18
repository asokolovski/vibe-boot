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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@WebMvcTest(DeploymentController.class)
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentService deploymentService;

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
                null
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
                .andExpect(jsonPath("$.finishedAt").doesNotExist());

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
                .andExpect(jsonPath("$.finishedAt").doesNotExist());

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
}
