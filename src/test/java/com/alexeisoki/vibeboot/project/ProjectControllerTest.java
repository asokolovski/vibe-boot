package com.alexeisoki.vibeboot.project;

import static org.mockito.ArgumentMatchers.any;
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

import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

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
                .andExpect(jsonPath("$.createdAt").value("2026-05-14T12:00:00Z"));

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
                .andExpect(jsonPath("$[0].createdAt").value("2026-05-14T12:00:00Z"));

        verify(projectService, times(1)).getAllProjects();
    }
}
