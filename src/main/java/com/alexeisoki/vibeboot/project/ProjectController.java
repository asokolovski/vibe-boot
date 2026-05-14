package com.alexeisoki.vibeboot.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectService.getAllProjects();
    }
}
