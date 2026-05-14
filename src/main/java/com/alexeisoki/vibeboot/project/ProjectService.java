package com.alexeisoki.vibeboot.project;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;

@Service
public class ProjectService {
    // The ProjectRepository is injected into the service, and we use it to interact with the database.
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse createProject(CreateProjectRequest request) {
        Project project = new Project(
                request.name(),
                request.repositoryUrl(),
                request.branch(),
                request.runCommand()
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

    // This is a helper method to get a Project by ID or throw an exception if it doesn't exist
    // Will come in handy later down the line when we want to create a deployment for a specific project, since we'll need to look up the project by ID first.
    public Project getProjectOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }

    // This is a helper method to convert a Project entity to a ProjectResponse DTO for the API.
    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getRepositoryUrl(),
                project.getBranch(),
                project.getRunCommand(),
                project.getCreatedAt()
        );
    }
}
