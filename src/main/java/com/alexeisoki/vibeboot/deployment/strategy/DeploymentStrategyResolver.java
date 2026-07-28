package com.alexeisoki.vibeboot.deployment.strategy;

import com.alexeisoki.vibeboot.project.ProjectSourceType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class DeploymentStrategyResolver {

    private final Map<ProjectSourceType, DeploymentStrategy> strategies =
            new EnumMap<>(ProjectSourceType.class);

    public DeploymentStrategyResolver(
            List<DeploymentStrategy> discoveredStrategies
    ) {
        for (DeploymentStrategy strategy : discoveredStrategies) {
            DeploymentStrategy existing =
                    strategies.put(strategy.sourceType(), strategy);

            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate strategy for " + strategy.sourceType()
                );
            }
        }
    }

    public DeploymentStrategy resolve(ProjectSourceType sourceType) {
        DeploymentStrategy strategy = strategies.get(sourceType);

        if (strategy == null) {
            throw new IllegalStateException(
                    "No deployment strategy for " + sourceType
            );
        }

        return strategy;
    }
}
