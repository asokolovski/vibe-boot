package com.alexeisoki.vibeboot.deployment.strategy;
import com.alexeisoki.vibeboot.project.ProjectSourceType;


public interface DeploymentStrategy {
    ProjectSourceType sourceType();
    
    void deploy(DeploymentExecutionContext context);
}
