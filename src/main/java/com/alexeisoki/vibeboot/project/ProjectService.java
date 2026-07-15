package com.alexeisoki.vibeboot.project;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Service
public class ProjectService {
    // The ProjectRepository is injected into the service, and we use it to interact with the database.
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse createProject(CreateProjectRequest request) {
        return createProject(request, null);
    }

    public ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId) {
        Project project = new Project(
                request.name(),
                repositoryUrlForPersistence(request),
                request.branch(),
                request.dockerfilePath(),
                request.containerPort(),
                request.healthCheckPath(),
                currentUserId,
                request.sourceType(),
                request.containerRegistry(),
                request.composeFilePath(),
                request.primaryServiceName()
        );

        Project savedProject = projectRepository.save(project);

        return toResponse(savedProject);
    }

    public List<ProjectResponse> getAllProjects() {
        // findAll returns a List<Project>, but we want to return a List<ProjectResponse> for the API, so we need to convert each Project to a ProjectResponse.
        List<Project> projects = projectRepository.findAll();
        List<ProjectResponse> responses = new ArrayList<>();
        for (Project project : projects) {
            responses.add(toResponse(project));
        }

        return responses;
    }

    public List<ProjectResponse> getProjectsForUser(UUID currentUserId) {
        List<Project> projects = projectRepository.findByOwnerUserId(currentUserId);
        List<ProjectResponse> responses = new ArrayList<>();
        for (Project project : projects) {
            responses.add(toResponse(project));
        }

        return responses;
    }

    // This is a helper method to get a Project by ID or throw an exception if it doesn't exist
    // Will come in handy later down the line when we want to create a deployment for a specific project, since we'll need to look up the project by ID first.
    public Project getProjectOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    public Project getProjectForUserOrThrow(UUID id, UUID currentUserId) {
        return projectRepository.findByIdAndOwnerUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    // This is a helper method to convert a Project entity to a ProjectResponse DTO for the API.
    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getRepositoryUrl(),
                project.getSourceType(),
                project.getContainerRegistry(),
                project.getBranch(),
                project.getDockerfilePath(),
                project.getContainerPort(),
                project.getHealthCheckPath(),
                project.getComposeFilePath(),
                project.getPrimaryServiceName(),
                project.getOwnerUserId(),
                project.getCreatedAt()
        );
    }

    private String repositoryUrlForPersistence(CreateProjectRequest request) {
        if (request.repositoryUrl() != null && !request.repositoryUrl().isBlank()) {
            return request.repositoryUrl();
        }

        if (request.sourceType() == ProjectSourceType.CONTAINER_IMAGE) {
            return repositoryUrlFromContainerRegistry(request.containerRegistry());
        }

        return request.repositoryUrl();
    }

    private String repositoryUrlFromContainerRegistry(String containerRegistry) {
        if (containerRegistry == null || containerRegistry.isBlank()) {
            return null;
        }

        String[] pathParts = containerRegistry.replaceFirst("^ghcr\\.io/", "").split("/");
        if (pathParts.length >= 2) {
            return "https://github.com/" + pathParts[0] + "/" + pathParts[1];
        }

        return "https://github.com/octocat/Hello-World";
    }
}
