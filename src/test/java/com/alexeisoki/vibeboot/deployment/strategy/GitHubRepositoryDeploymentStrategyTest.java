package com.alexeisoki.vibeboot.deployment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
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
import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.GitCloneResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
import com.alexeisoki.vibeboot.project.ProjectSourceType;

@ExtendWith(MockitoExtension.class)
class GitHubRepositoryDeploymentStrategyTest {
    private static final Path WORKSPACE = Path.of("/tmp/vibeboot-workspaces/deployment-test");
    private static final Path SOURCE_DIRECTORY = WORKSPACE.resolve("source");

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentLogService deploymentLogService;

    @Mock
    private DockerService dockerService;

    @Mock
    private PortAllocator portAllocator;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private GitService gitService;

    @Mock
    private ProjectEnvironmentVariableService environmentVariableService;

    @Test
    void deploy_clonesBuildsAndStartsContainer() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = githubProject();
        Map<String, String> environmentVariables = Map.of("NODE_ENV", "production");
        String imageName = "vibeboot-demo:" + deploymentId;
        GitHubRepositoryDeploymentStrategy strategy = strategy();
        DeploymentExecutionContext context =
                new DeploymentExecutionContext(deploymentId, deployment, project);

        when(workspaceService.createWorkspace(deploymentId)).thenReturn(WORKSPACE);
        when(gitService.cloneRepository(project.getRepositoryUrl(), project.getBranch(), SOURCE_DIRECTORY))
                .thenReturn(new GitCloneResult("clone ok"));
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, "build ok"));
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

        assertThat(strategy.sourceType()).isEqualTo(ProjectSourceType.GITHUB_REPOSITORY);
        assertThat(context.workspace()).isEqualTo(WORKSPACE);
        assertThat(deployment.getRuntimeType()).isEqualTo(DeploymentRuntimeType.SINGLE_CONTAINER);
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isEqualTo("container-123");
        verify(gitService).cloneRepository(
                project.getRepositoryUrl(),
                project.getBranch(),
                SOURCE_DIRECTORY
        );
        verify(dockerService).buildImage(deploymentId, project, SOURCE_DIRECTORY);
        verify(dockerService).runContainer(
                deploymentId,
                project,
                imageName,
                49152,
                environmentVariables
        );
        verify(deploymentRepository, times(2)).save(deployment);
        verify(workspaceService, never()).cleanupWorkspace(WORKSPACE);
    }

    private GitHubRepositoryDeploymentStrategy strategy() {
        return new GitHubRepositoryDeploymentStrategy(
                deploymentRepository,
                deploymentLogService,
                dockerService,
                portAllocator,
                workspaceService,
                gitService,
                environmentVariableService
        );
    }

    private Project githubProject() {
        return new Project(
                "Demo",
                "https://github.com/asokolovski/demo",
                "main",
                "Dockerfile",
                8080,
                "/health"
        );
    }
}
