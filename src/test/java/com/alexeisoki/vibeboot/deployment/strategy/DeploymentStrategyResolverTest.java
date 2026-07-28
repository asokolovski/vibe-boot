package com.alexeisoki.vibeboot.deployment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alexeisoki.vibeboot.project.ProjectSourceType;

class DeploymentStrategyResolverTest {

    @Test
    void resolve_returnsStrategyMatchingSourceType() {
        DeploymentStrategy containerImageStrategy = strategy(ProjectSourceType.CONTAINER_IMAGE);
        DeploymentStrategy githubStrategy = strategy(ProjectSourceType.GITHUB_REPOSITORY);
        DeploymentStrategy composeStrategy = strategy(ProjectSourceType.DOCKER_COMPOSE);
        DeploymentStrategyResolver resolver = new DeploymentStrategyResolver(
                List.of(containerImageStrategy, githubStrategy, composeStrategy)
        );

        assertThat(resolver.resolve(ProjectSourceType.CONTAINER_IMAGE)).isSameAs(containerImageStrategy);
        assertThat(resolver.resolve(ProjectSourceType.GITHUB_REPOSITORY)).isSameAs(githubStrategy);
        assertThat(resolver.resolve(ProjectSourceType.DOCKER_COMPOSE)).isSameAs(composeStrategy);
    }

    @Test
    void constructor_rejectsDuplicateSourceTypes() {
        DeploymentStrategy first = strategy(ProjectSourceType.CONTAINER_IMAGE);
        DeploymentStrategy second = strategy(ProjectSourceType.CONTAINER_IMAGE);

        assertThatThrownBy(() -> new DeploymentStrategyResolver(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate strategy for CONTAINER_IMAGE");
    }

    @Test
    void resolve_rejectsMissingStrategy() {
        DeploymentStrategyResolver resolver = new DeploymentStrategyResolver(List.of());

        assertThatThrownBy(() -> resolver.resolve(ProjectSourceType.DOCKER_COMPOSE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No deployment strategy for DOCKER_COMPOSE");
    }

    private DeploymentStrategy strategy(ProjectSourceType sourceType) {
        DeploymentStrategy strategy = mock(DeploymentStrategy.class);
        when(strategy.sourceType()).thenReturn(sourceType);
        return strategy;
    }
}
