package com.alexeisoki.vibeboot.deployment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileResult;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileService;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.GitCloneResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
import com.alexeisoki.vibeboot.project.ProjectSourceType;

@ExtendWith(MockitoExtension.class)
class DockerComposeDeploymentStrategyTest {
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

    @Mock
    private ComposeFileService composeFileService;

    @Test
    void deploy_clonesAndStartsComposeProject() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = composeProject();
        Path generatedComposeFile = SOURCE_DIRECTORY.resolve("vibeboot.compose.yaml");
        Map<String, String> environmentVariables = Map.of("API_KEY", "secret");
        DockerComposeDeploymentStrategy strategy = strategy();
        DeploymentExecutionContext context =
                new DeploymentExecutionContext(deploymentId, deployment, project);

        when(workspaceService.createWorkspace(deploymentId)).thenReturn(WORKSPACE);
        when(gitService.cloneRepository(project.getRepositoryUrl(), project.getBranch(), SOURCE_DIRECTORY))
                .thenReturn(new GitCloneResult("clone ok"));
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId()))
                .thenReturn(environmentVariables);
        when(composeFileService.createVibeBootComposeFile(project, SOURCE_DIRECTORY, 49152))
                .thenReturn(new ComposeFileResult(generatedComposeFile, 80));
        when(dockerService.runCompose(
                deploymentId,
                project,
                generatedComposeFile,
                49152,
                80,
                environmentVariables
        )).thenReturn(new ComposeRunResult(
                "vibeboot-" + deploymentId,
                "frontend",
                49152,
                80,
                "http://localhost:49152",
                "compose ok"
        ));

        strategy.deploy(context);

        assertThat(strategy.sourceType()).isEqualTo(ProjectSourceType.DOCKER_COMPOSE);
        assertThat(context.workspace()).isEqualTo(WORKSPACE);
        assertThat(deployment.getRuntimeType()).isEqualTo(DeploymentRuntimeType.DOCKER_COMPOSE);
        assertThat(deployment.getComposeProjectName()).isEqualTo("vibeboot-" + deploymentId);
        assertThat(deployment.getPrimaryServiceName()).isEqualTo("frontend");
        assertThat(deployment.getDeploymentUrl()).isEqualTo("http://localhost:49152");
        verify(composeFileService).createVibeBootComposeFile(project, SOURCE_DIRECTORY, 49152);
        verify(dockerService).runCompose(
                deploymentId,
                project,
                generatedComposeFile,
                49152,
                80,
                environmentVariables
        );
        verify(deploymentRepository).save(deployment);
        verify(workspaceService, never()).cleanupWorkspace(WORKSPACE);
    }

    private DockerComposeDeploymentStrategy strategy() {
        return new DockerComposeDeploymentStrategy(
                deploymentRepository,
                deploymentLogService,
                dockerService,
                portAllocator,
                workspaceService,
                gitService,
                environmentVariableService,
                composeFileService
        );
    }

    private Project composeProject() {
        return new Project(
                "Demo",
                "https://github.com/asokolovski/demo",
                "main",
                null,
                null,
                "/health",
                null,
                ProjectSourceType.DOCKER_COMPOSE,
                null,
                "compose.yaml",
                "frontend"
        );
    }
}
