package com.alexeisoki.vibeboot.project;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alexeisoki.vibeboot.deployment.DeploymentService;
import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.project.dto.AddProjectEnvironmentVariableRequest;
import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectEnvironmentVariableResponse;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final DeploymentService deploymentService;
    private final ProjectEnvironmentVariableService environmentVariableService;

    public ProjectController(
            ProjectService projectService,
            DeploymentService deploymentService,
            ProjectEnvironmentVariableService environmentVariableService
    ) {
        this.projectService = projectService;
        this.deploymentService = deploymentService;
        this.environmentVariableService = environmentVariableService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{projectId}/deployments")
    public List<DeploymentResponse> getDeploymentsForProject(@PathVariable UUID projectId) {
        return deploymentService.getDeploymentsForProject(projectId);
    }

    @PostMapping("/{projectId}/env")
    public ResponseEntity<ProjectEnvironmentVariableResponse> addEnvVar(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectEnvironmentVariableRequest request
    ) {
        ProjectEnvironmentVariableResponse response = environmentVariableService.addEnvVar(projectId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{projectId}/env")
    public List<ProjectEnvironmentVariableResponse> listEnvVars(@PathVariable UUID projectId) {
        return environmentVariableService.listEnvVars(projectId);
    }

    @DeleteMapping("/{projectId}/env/{envId}")
    public ResponseEntity<Void> deleteEnvVar(@PathVariable UUID projectId, @PathVariable UUID envId) {
        environmentVariableService.deleteEnvVar(projectId, envId);

        return ResponseEntity.noContent().build();
    }
}
