package com.alexeisoki.vibeboot.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.project.dto.AddProjectEnvironmentVariableRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectEnvironmentVariableResponse;
import com.alexeisoki.vibeboot.shared.EncryptionService;
import com.alexeisoki.vibeboot.shared.ResourceConflictException;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Service
public class ProjectEnvironmentVariableService {
    private static final Pattern ENV_VAR_KEY_PATTERN = Pattern.compile("[A-Z_][A-Z0-9_]*");

    private final ProjectEnvironmentVariableRepository environmentVariableRepository;
    private final ProjectService projectService;
    private final EncryptionService encryptionService;

    public ProjectEnvironmentVariableService(
            ProjectEnvironmentVariableRepository environmentVariableRepository,
            ProjectService projectService,
            EncryptionService encryptionService
    ) {
        this.environmentVariableRepository = environmentVariableRepository;
        this.projectService = projectService;
        this.encryptionService = encryptionService;
    }

    public ProjectEnvironmentVariableResponse addEnvVar(
            UUID projectId,
            AddProjectEnvironmentVariableRequest request
    ) {
        projectService.getProjectOrThrow(projectId);
        validateEnvVarRequest(request);

        if (environmentVariableRepository.existsByProjectIdAndKey(projectId, request.key())) {
            throw new ResourceConflictException("Project environment variable key already exists");
        }

        ProjectEnvironmentVariable environmentVariable = new ProjectEnvironmentVariable(
                projectId,
                request.key(),
                encryptionService.encrypt(request.value())
        );

        try {
            return toResponse(environmentVariableRepository.save(environmentVariable));
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("Project environment variable key already exists");
        }
    }

    public List<ProjectEnvironmentVariableResponse> listEnvVars(UUID projectId) {
        projectService.getProjectOrThrow(projectId);

        List<ProjectEnvironmentVariable> environmentVariables =
                environmentVariableRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<ProjectEnvironmentVariableResponse> responses = new ArrayList<>();
        for (ProjectEnvironmentVariable environmentVariable : environmentVariables) {
            responses.add(toResponse(environmentVariable));
        }

        return responses;
    }

    public void deleteEnvVar(UUID projectId, UUID envId) {
        projectService.getProjectOrThrow(projectId);

        ProjectEnvironmentVariable environmentVariable = environmentVariableRepository.findByIdAndProjectId(envId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project environment variable not found"));

        environmentVariableRepository.delete(environmentVariable);
    }

    public Map<String, String> getDecryptedEnvVarsForProject(UUID projectId) {
        projectService.getProjectOrThrow(projectId);

        List<ProjectEnvironmentVariable> environmentVariables =
                environmentVariableRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        Map<String, String> decryptedEnvVars = new LinkedHashMap<>();
        for (ProjectEnvironmentVariable environmentVariable : environmentVariables) {
            decryptedEnvVars.put(
                    environmentVariable.getKey(),
                    encryptionService.decrypt(environmentVariable.getValueEncrypted())
            );
        }

        return decryptedEnvVars;
    }

    private void validateEnvVarRequest(AddProjectEnvironmentVariableRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        if (request.key() == null || !ENV_VAR_KEY_PATTERN.matcher(request.key()).matches()) {
            throw new IllegalArgumentException("key must match [A-Z_][A-Z0-9_]*");
        }

        if (request.value() == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    private ProjectEnvironmentVariableResponse toResponse(ProjectEnvironmentVariable environmentVariable) {
        return new ProjectEnvironmentVariableResponse(
                environmentVariable.getId(),
                environmentVariable.getProjectId(),
                environmentVariable.getKey(),
                environmentVariable.getCreatedAt()
        );
    }
}
