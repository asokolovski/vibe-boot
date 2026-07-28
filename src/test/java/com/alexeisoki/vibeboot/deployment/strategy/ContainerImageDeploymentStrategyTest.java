package com.alexeisoki.vibeboot.deployment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexeisoki.vibeboot.deployment.Deployment;
import com.alexeisoki.vibeboot.deployment.DeploymentLogService;
import com.alexeisoki.vibeboot.deployment.DeploymentRepository;
import com.alexeisoki.vibeboot.deployment.DeploymentRuntimeType;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
import com.alexeisoki.vibeboot.project.ProjectSourceType;

@ExtendWith(MockitoExtension.class)
class ContainerImageDeploymentStrategyTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DockerService dockerService;

    @Mock
    private DeploymentLogService deploymentLogService;

    @Mock
    private PortAllocator portAllocator;

    @Mock
    private ProjectEnvironmentVariableService environmentVariableService;

    @Test
    void deploy_pullsImageAndStartsContainer() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID(), "sha-4a928d5");
        Project project = containerImageProject();
        Map<String, String> environmentVariables = Map.of("API_KEY", "secret");
        String imageName = "ghcr.io/asokolovski/demo:sha-4a928d5";
        ContainerImageDeploymentStrategy strategy = strategy();
        DeploymentExecutionContext context =
                new DeploymentExecutionContext(deploymentId, deployment, project);

        when(dockerService.pullImage(imageName)).thenReturn("pull ok");
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId()))
                .thenReturn(environmentVariables);
        when(dockerService.runContainer(
                deploymentId,
                project,
                imageName,
                49152,
                environmentVariables
        )).thenReturn(new DockerRunResult("container-123", 49152, 8080, "http://localhost:49152"));

        strategy.deploy(context);

        assertThat(strategy.sourceType()).isEqualTo(ProjectSourceType.CONTAINER_IMAGE);
        assertThat(context.workspace()).isNull();
        assertThat(deployment.getRuntimeType()).isEqualTo(DeploymentRuntimeType.SINGLE_CONTAINER);
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isEqualTo("container-123");
        assertThat(deployment.getDeploymentUrl()).isEqualTo("http://localhost:49152");
        verify(dockerService).pullImage(imageName);
        verify(dockerService).runContainer(
                deploymentId,
                project,
                imageName,
                49152,
                environmentVariables
        );
        verify(deploymentRepository, times(2)).save(deployment);
    }

    private ContainerImageDeploymentStrategy strategy() {
        return new ContainerImageDeploymentStrategy(
                deploymentRepository,
                dockerService,
                deploymentLogService,
                portAllocator,
                environmentVariableService
        );
    }

    private Project containerImageProject() {
        return new Project(
                "Demo",
                null,
                null,
                null,
                8080,
                "/health",
                null,
                ProjectSourceType.CONTAINER_IMAGE,
                "ghcr.io/asokolovski/demo"
        );
    }
}
