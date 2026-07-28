package com.alexeisoki.vibeboot.deployment.strategy;

import java.nio.file.Path;
import java.util.UUID;

import com.alexeisoki.vibeboot.deployment.Deployment;
import com.alexeisoki.vibeboot.project.Project;

public class DeploymentExecutionContext {

    private final UUID deploymentId;
    private final Deployment deployment;
    private final Project project;
    private Path workspace;

    public DeploymentExecutionContext(
            UUID deploymentId,
            Deployment deployment,
            Project project
    ) {
        this.deploymentId = deploymentId;
        this.deployment = deployment;
        this.project = project;
    }

    public UUID deploymentId() {
        return deploymentId;
    }

    public Deployment deployment() {
        return deployment;
    }

    public Project project() {
        return project;
    }

    public Path workspace() {
        return workspace;
    }

    public void registerWorkspace(Path workspace) {
        this.workspace = workspace;
    }
}