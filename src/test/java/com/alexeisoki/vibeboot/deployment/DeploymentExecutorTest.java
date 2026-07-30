package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.DockerServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileResult;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeFileService;
import com.alexeisoki.vibeboot.deployment.runtime.ComposeRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitCloneResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitService;
import com.alexeisoki.vibeboot.deployment.runtime.GitServiceException;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckResult;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceServiceException;
import com.alexeisoki.vibeboot.deployment.strategy.ContainerImageDeploymentStrategy;
import com.alexeisoki.vibeboot.deployment.strategy.DeploymentExecutionContext;
import com.alexeisoki.vibeboot.deployment.strategy.DeploymentStrategy;
import com.alexeisoki.vibeboot.deployment.strategy.DeploymentStrategyResolver;
import com.alexeisoki.vibeboot.deployment.strategy.DockerComposeDeploymentStrategy;
import com.alexeisoki.vibeboot.deployment.strategy.GitHubRepositoryDeploymentStrategy;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.project.ProjectSourceType;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeploymentExecutorTest {
    private static final Path WORKSPACE = Path.of("/tmp/vibeboot-workspaces/deployment-test");
    private static final Path SOURCE_DIRECTORY = WORKSPACE.resolve("source");

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentLogService deploymentLogService;

    @Mock
    private ProjectService projectService;

    @Mock
    private DockerService dockerService;

    @Mock
    private PortAllocator portAllocator;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private GitService gitService;

    @Mock
    private ProjectEnvironmentVariableService environmentVariableService;

    @Mock
    private ComposeFileService composeFileService;

    @Test
    void execute_clonesBuildsInjectsEnvVarsRunsHealthCheckAndCleansWorkspace() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        Map<String, String> environmentVariables = Map.of("API_KEY", "secret", "NODE_ENV", "production");
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "clone ok");
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, "build ok"));
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId()))
                .thenReturn(environmentVariables);
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152, environmentVariables))
                .thenReturn(new DockerRunResult("abc123", 49152, 8080, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/health"))
                .thenReturn(new HealthCheckResult(
                        true,
                        URI.create("http://localhost:49152/health"),
                        2,
                        "Health check succeeded"
                ));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isEqualTo("abc123");

        InOrder serviceOrder = inOrder(
                workspaceService,
                gitService,
                dockerService,
                environmentVariableService,
                healthCheckService
        );
        serviceOrder.verify(workspaceService).createWorkspace(deploymentId);
        serviceOrder.verify(gitService).cloneRepository(
                project.getRepositoryUrl(),
                project.getBranch(),
                SOURCE_DIRECTORY
        );
        serviceOrder.verify(dockerService).buildImage(deploymentId, project, SOURCE_DIRECTORY);
        serviceOrder.verify(environmentVariableService).getDecryptedEnvVarsForProject(deployment.getProjectId());
        serviceOrder.verify(dockerService).runContainer(deploymentId, project, imageName, 49152, environmentVariables);
        serviceOrder.verify(healthCheckService).waitUntilHealthy("http://localhost:49152", "/health");
        serviceOrder.verify(workspaceService).cleanupWorkspace(WORKSPACE);

        InOrder logOrder = inOrder(deploymentLogService);
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment started");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Created workspace");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Cloning repository");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "clone ok");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Repository cloned successfully");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Building Docker image");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "build ok");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker image built: " + imageName);
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Loading project environment variables");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Loaded 2 project environment variable(s)");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Starting Docker container");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker container started: abc123");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment URL: http://localhost:49152");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Running health check: http://localhost:49152/health");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Health check succeeded after 2 attempt(s)");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment succeeded");
        logOrder.verify(deploymentLogService).appendLog(deploymentId, "Workspace cleaned up");
    }

    @Test
    void execute_pullsRegistryImageRunsHealthCheckAndSkipsWorkspace() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID(), "sha-4a928d5");
        Project project = containerImageProject();
        String imageName = "ghcr.io/asokolovski/vb-gha-demo-app:sha-4a928d5";
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        when(dockerService.pullImage(imageName)).thenReturn("pull ok");
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId())).thenReturn(Map.of());
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152, Map.of()))
                .thenReturn(new DockerRunResult("abc123", 49152, 80, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/"))
                .thenReturn(new HealthCheckResult(
                        true,
                        URI.create("http://localhost:49152/"),
                        1,
                        "Health check succeeded"
                ));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isEqualTo("abc123");

        verify(workspaceService, never()).createWorkspace(any(UUID.class));
        verify(gitService, never()).cloneRepository(anyString(), anyString(), any(Path.class));
        verify(dockerService, never()).buildImage(any(UUID.class), any(Project.class), any(Path.class));
        verify(dockerService).pullImage(imageName);
        verify(dockerService).runContainer(deploymentId, project, imageName, 49152, Map.of());
        verify(healthCheckService).waitUntilHealthy("http://localhost:49152", "/");
    }

    @Test
    void execute_clonesSanitizesAndRunsDockerComposeProject() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = composeProject();
        Path composeFile = SOURCE_DIRECTORY.resolve("vibeboot.compose.yaml");
        Map<String, String> environmentVariables = Map.of("LLM_API_KEY", "secret");
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "clone ok");
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId()))
                .thenReturn(environmentVariables);
        when(composeFileService.createVibeBootComposeFile(project, SOURCE_DIRECTORY, 49152))
                .thenReturn(new ComposeFileResult(composeFile, 80));
        when(dockerService.runCompose(deploymentId, project, composeFile, 49152, 80, environmentVariables))
                .thenReturn(new ComposeRunResult(
                        "vibeboot-" + deploymentId,
                        "frontend",
                        49152,
                        80,
                        "http://localhost:49152",
                        "compose ok"
                ));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/"))
                .thenReturn(new HealthCheckResult(
                        true,
                        URI.create("http://localhost:49152/"),
                        1,
                        "Health check succeeded"
                ));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getRuntimeType()).isEqualTo(DeploymentRuntimeType.DOCKER_COMPOSE);
        assertThat(deployment.getComposeProjectName()).isEqualTo("vibeboot-" + deploymentId);
        assertThat(deployment.getPrimaryServiceName()).isEqualTo("frontend");
        assertThat(deployment.getHostPort()).isEqualTo(49152);
        assertThat(deployment.getContainerPort()).isEqualTo(80);

        verify(composeFileService).createVibeBootComposeFile(project, SOURCE_DIRECTORY, 49152);
        verify(dockerService).runCompose(deploymentId, project, composeFile, 49152, 80, environmentVariables);
        verify(dockerService, never()).buildImage(any(UUID.class), any(Project.class), any(Path.class));
        verify(dockerService, never()).runContainer(any(UUID.class), any(Project.class), anyString(), anyInt(), anyMap());
        verify(healthCheckService).waitUntilHealthy("http://localhost:49152", "/");
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
    }

    @Test
    void execute_marksFailedAndCleansWorkspaceWhenCloneFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        when(workspaceService.createWorkspace(deploymentId)).thenReturn(WORKSPACE);
        when(gitService.cloneRepository(project.getRepositoryUrl(), project.getBranch(), SOURCE_DIRECTORY))
                .thenThrow(new GitServiceException("Git repository clone failed: missing branch", "fatal: missing branch"));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(deploymentLogService).appendLog(deploymentId, "fatal: missing branch");
        verify(deploymentLogService).appendLog(deploymentId, "Git repository clone failed: missing branch");
        verify(dockerService, never()).buildImage(any(UUID.class), any(Project.class), any(Path.class));
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
        verify(deploymentLogService).appendLog(deploymentId, "Workspace cleaned up");
    }

    @Test
    void execute_marksFailedAndCleansWorkspaceWhenDockerBuildFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "");
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenThrow(new DockerServiceException("Docker image build failed: bad Dockerfile", "bad Dockerfile"));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(deploymentLogService).appendLog(deploymentId, "bad Dockerfile");
        verify(environmentVariableService, never()).getDecryptedEnvVarsForProject(any(UUID.class));
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
    }

    @Test
    void execute_marksFailedAndCleansWorkspaceWhenEnvVarDecryptFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "");
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId()))
                .thenThrow(new IllegalArgumentException("encryptedValue could not be decrypted"));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(dockerService, never()).runContainer(
                any(UUID.class),
                any(Project.class),
                anyString(),
                anyInt(),
                anyMap()
        );
        verify(deploymentLogService).appendLog(deploymentId, "encryptedValue could not be decrypted");
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
    }

    @Test
    void execute_marksFailedAndCleansWorkspaceWhenDockerRunFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "");
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId())).thenReturn(Map.of());
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152, Map.of()))
                .thenThrow(new DockerServiceException("Docker container run failed: port allocated", "port allocated"));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(deploymentLogService).appendLog(deploymentId, "port allocated");
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
    }

    @Test
    void execute_stopsUnhealthyContainerMarksFailedAndCleansWorkspace() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "");
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId())).thenReturn(Map.of());
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152, Map.of()))
                .thenReturn(new DockerRunResult("abc123", 49152, 8080, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/health"))
                .thenReturn(new HealthCheckResult(
                        false,
                        URI.create("http://localhost:49152/health"),
                        30,
                        "Health check timed out"
                ));
        when(dockerService.getContainerLogs("abc123")).thenReturn("App failed to become healthy");

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(dockerService).getContainerLogs("abc123");
        verify(dockerService).stopContainer("abc123");
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
    }

    @Test
    void execute_logsWorkspaceCleanupFailureWithoutOverwritingSuccess() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        stubRunningDeployment(deploymentId, deployment, project);
        stubWorkspaceAndClone(deploymentId, project, "");
        when(dockerService.buildImage(deploymentId, project, SOURCE_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(environmentVariableService.getDecryptedEnvVarsForProject(deployment.getProjectId())).thenReturn(Map.of());
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152, Map.of()))
                .thenReturn(new DockerRunResult("abc123", 49152, 8080, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/health"))
                .thenReturn(new HealthCheckResult(
                        true,
                        URI.create("http://localhost:49152/health"),
                        1,
                        "Health check succeeded"
                ));
        org.mockito.Mockito.doThrow(new WorkspaceServiceException("disk busy", new IOException("disk busy")))
                .when(workspaceService).cleanupWorkspace(WORKSPACE);

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        verify(deploymentLogService).appendLog(deploymentId, "Could not clean up workspace: disk busy");
    }

    @Test
    void execute_cleansWorkspaceRegisteredBeforeStrategyFailure() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        DeploymentStrategyResolver strategyResolver = mock(DeploymentStrategyResolver.class);
        DeploymentStrategy strategy = mock(DeploymentStrategy.class);
        DeploymentExecutor deploymentExecutor = deploymentExecutor(strategyResolver);

        stubRunningDeployment(deploymentId, deployment, project);
        when(strategyResolver.resolve(project.getSourceType())).thenReturn(strategy);
        doAnswer(invocation -> {
            DeploymentExecutionContext context = invocation.getArgument(0);
            context.registerWorkspace(WORKSPACE);
            throw new GitServiceException(
                    "Git repository clone failed: repository unavailable",
                    "fatal: repository unavailable"
            );
        }).when(strategy).deploy(any(DeploymentExecutionContext.class));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(workspaceService).cleanupWorkspace(WORKSPACE);
        verify(deploymentLogService).appendLog(deploymentId, "fatal: repository unavailable");
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());
    }

    @Test
    void execute_marksFailedWhenProjectLookupFailsWithoutAttemptingWorkspaceCleanup() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentStrategyResolver strategyResolver = mock(DeploymentStrategyResolver.class);
        DeploymentExecutor deploymentExecutor = deploymentExecutor(strategyResolver);

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId()))
                .thenThrow(new ResourceNotFoundException("Project not found"));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        verify(strategyResolver, never()).resolve(any(ProjectSourceType.class));
        verify(workspaceService, never()).cleanupWorkspace(any(Path.class));
        verify(deploymentLogService).appendLog(deploymentId, "Project not found");
    }

    @Test
    void execute_skipsDeploymentThatIsAlreadyRunning() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markRunning();
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        verify(workspaceService, never()).createWorkspace(any(UUID.class));
        verify(gitService, never()).cloneRepository(anyString(), anyString(), any(Path.class));
        verify(deploymentRepository, never()).save(any(Deployment.class));
    }

    @Test
    void execute_recoversRedeliveredRunningDeploymentAndRetriesIt() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markRunning();
        deployment.recordDockerRuntime(
                "old-image",
                "old-container",
                49152,
                8080,
                "http://localhost:49152"
        );
        Project project = project();
        DeploymentStrategyResolver strategyResolver = mock(DeploymentStrategyResolver.class);
        DeploymentStrategy strategy = mock(DeploymentStrategy.class);
        DeploymentExecutor deploymentExecutor = deploymentExecutor(strategyResolver);

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(strategyResolver.resolve(project.getSourceType())).thenReturn(strategy);
        doAnswer(invocation -> {
            deployment.recordDockerRuntime(
                    "new-image",
                    "new-container",
                    49153,
                    8080,
                    "http://localhost:49153"
            );
            return null;
        }).when(strategy).deploy(any(DeploymentExecutionContext.class));
        when(healthCheckService.waitUntilHealthy("http://localhost:49153", "/health"))
                .thenReturn(new HealthCheckResult(
                        true,
                        URI.create("http://localhost:49153/health"),
                        1,
                        "Health check succeeded"
                ));

        deploymentExecutor.execute(deploymentId, true);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getAttemptCount()).isEqualTo(2);
        assertThat(deployment.getContainerId()).isEqualTo("new-container");
        verify(dockerService).getContainerLogs("old-container");
        verify(dockerService).stopContainer("old-container");
        verify(deploymentLogService).appendLog(
                deploymentId,
                "Previous deployment attempt was interrupted; retrying"
        );
        verify(strategy).deploy(any(DeploymentExecutionContext.class));
    }

    @Test
    void execute_marksRedeliveredRunningDeploymentFailedAfterMaximumAttempts() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markRunning();
        deployment.markRunning();
        deployment.markRunning();
        deployment.recordDockerRuntime(
                "old-image",
                "old-container",
                49152,
                8080,
                "http://localhost:49152"
        );
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentExecutor.execute(deploymentId, true);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getAttemptCount()).isEqualTo(3);
        verify(dockerService).stopContainer("old-container");
        verify(deploymentRepository, never()).markRunningIfQueued(
                any(UUID.class),
                any(Instant.class),
                any(DeploymentStatus.class),
                any(DeploymentStatus.class)
        );
        verify(projectService, never()).getProjectOrThrow(any(UUID.class));
        verify(deploymentLogService).appendLog(
                deploymentId,
                "Deployment worker was interrupted too many times"
        );
    }

    @Test
    void execute_skipsRedeliveredTerminalDeployment() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markFinished(DeploymentStatus.SUCCESS);
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentExecutor.execute(deploymentId, true);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        verify(dockerService, never()).stopContainer(anyString());
        verify(deploymentRepository, never()).markRunningIfQueued(
                any(UUID.class),
                any(Instant.class),
                any(DeploymentStatus.class),
                any(DeploymentStatus.class)
        );
        verify(deploymentRepository, never()).save(any(Deployment.class));
    }

    @Test
    void execute_throwsWhenDeploymentIsMissing() {
        UUID deploymentId = UUID.randomUUID();
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentExecutor.execute(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment not found");

        verify(deploymentRepository).findById(deploymentId);
    }

    private void stubRunningDeployment(UUID deploymentId, Deployment deployment, Project project) {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubWorkspaceAndClone(UUID deploymentId, Project project, String cloneOutput) {
        when(workspaceService.createWorkspace(deploymentId)).thenReturn(WORKSPACE);
        when(gitService.cloneRepository(project.getRepositoryUrl(), project.getBranch(), SOURCE_DIRECTORY))
                .thenReturn(new GitCloneResult(cloneOutput));
    }

    private DeploymentExecutor deploymentExecutor() {
        DeploymentStrategyResolver strategyResolver = new DeploymentStrategyResolver(
                List.of(
                        new ContainerImageDeploymentStrategy(
                                deploymentRepository,
                                dockerService,
                                deploymentLogService,
                                portAllocator,
                                environmentVariableService
                        ),
                        new GitHubRepositoryDeploymentStrategy(
                                deploymentRepository,
                                deploymentLogService,
                                dockerService,
                                portAllocator,
                                workspaceService,
                                gitService,
                                environmentVariableService
                        ),
                        new DockerComposeDeploymentStrategy(
                                deploymentRepository,
                                deploymentLogService,
                                dockerService,
                                portAllocator,
                                workspaceService,
                                gitService,
                                environmentVariableService,
                                composeFileService
                        )
                )
        );

        return deploymentExecutor(strategyResolver);
    }

    private DeploymentExecutor deploymentExecutor(DeploymentStrategyResolver strategyResolver) {
        return new DeploymentExecutor(
                deploymentRepository,
                deploymentLogService,
                projectService,
                dockerService,
                healthCheckService,
                workspaceService,
                strategyResolver
        );
    }

    private Project project() {
        return new Project(
                "Vibe Boot",
                "https://github.com/alexeisoko/vibe-boot",
                "main",
                "Dockerfile",
                8080,
                "/health"
        );
    }

    private Project containerImageProject() {
        return new Project(
                "GHA Demo",
                null,
                null,
                null,
                80,
                "/",
                null,
                ProjectSourceType.CONTAINER_IMAGE,
                "ghcr.io/asokolovski/vb-gha-demo-app"
        );
    }

    private Project composeProject() {
        return new Project(
                "YT Clipper",
                "https://github.com/asokolovski/yt-clipper-mvp",
                "main",
                null,
                null,
                "/",
                null,
                ProjectSourceType.DOCKER_COMPOSE,
                null,
                "compose.yaml",
                "frontend"
        );
    }
}
