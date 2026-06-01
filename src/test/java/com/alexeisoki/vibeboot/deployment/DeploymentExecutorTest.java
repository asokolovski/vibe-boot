package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
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
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckResult;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeploymentExecutorTest {

    private static final Path PROJECT_DIRECTORY = Path.of("/home/alexei/projects/sample-app");

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

    @Test
    void execute_buildsImageRunsContainerSavesRuntimeThenSuccess() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(dockerService.buildImage(deploymentId, project, PROJECT_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, "Step 1/2"));
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152))
                .thenReturn(new DockerRunResult("abc123", 49152, 8080, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/health"))
                .thenReturn(new HealthCheckResult(
                        true,
                        URI.create("http://localhost:49152/health"),
                        2,
                        "Health check succeeded"
                ));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isEqualTo("abc123");
        assertThat(deployment.getHostPort()).isEqualTo(49152);
        assertThat(deployment.getContainerPort()).isEqualTo(8080);
        assertThat(deployment.getDeploymentUrl()).isEqualTo("http://localhost:49152");

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository).markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        );
        verify(projectService).getProjectOrThrow(deployment.getProjectId());
        verify(dockerService).buildImage(deploymentId, project, PROJECT_DIRECTORY);
        verify(portAllocator).allocatePort();
        verify(dockerService).runContainer(deploymentId, project, imageName, 49152);
        verify(healthCheckService).waitUntilHealthy("http://localhost:49152", "/health");
        verify(deploymentRepository, times(3)).save(deployment);

        InOrder inOrder = inOrder(deploymentLogService, deploymentRepository);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment started");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Building Docker image");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Step 1/2");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker image built: " + imageName);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Starting Docker container");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker container started: abc123");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment URL: http://localhost:49152");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Running health check: http://localhost:49152/health");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Health check succeeded after 2 attempt(s)");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment succeeded");
    }

    @Test
    void execute_marksDeploymentFailedWhenDockerBuildFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(dockerService.buildImage(deploymentId, project, PROJECT_DIRECTORY))
                .thenThrow(new DockerServiceException("Docker image build failed: bad Dockerfile", "bad Dockerfile"));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();
        assertThat(deployment.getImageName()).isNull();

        verify(deploymentRepository).save(deployment);
        verify(dockerService).buildImage(deploymentId, project, PROJECT_DIRECTORY);
        verify(portAllocator, never()).allocatePort();
        verify(dockerService, never()).runContainer(any(UUID.class), any(Project.class), any(String.class), anyInt());
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());

        InOrder inOrder = inOrder(deploymentLogService, deploymentRepository);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment started");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Building Docker image");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "bad Dockerfile");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker image build failed: bad Dockerfile");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment failed");
    }

    @Test
    void execute_marksDeploymentFailedWhenDockerRunFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(dockerService.buildImage(deploymentId, project, PROJECT_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152))
                .thenThrow(new DockerServiceException("Docker container run failed: port already allocated", "port already allocated"));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isNull();
        assertThat(deployment.getHostPort()).isNull();
        assertThat(deployment.getContainerPort()).isNull();
        assertThat(deployment.getDeploymentUrl()).isNull();

        verify(deploymentRepository, times(2)).save(deployment);
        verify(portAllocator).allocatePort();
        verify(dockerService).runContainer(deploymentId, project, imageName, 49152);
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());

        InOrder inOrder = inOrder(deploymentLogService, deploymentRepository);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment started");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Building Docker image");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker image built: " + imageName);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Starting Docker container");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "port already allocated");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Docker container run failed: port already allocated");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment failed");
    }

    @Test
    void execute_marksDeploymentFailedWhenHealthCheckFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(dockerService.buildImage(deploymentId, project, PROJECT_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152))
                .thenReturn(new DockerRunResult("abc123", 49152, 8080, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/health"))
                .thenReturn(new HealthCheckResult(
                        false,
                        URI.create("http://localhost:49152/health"),
                        30,
                        "Health check timed out"
                ));
        when(dockerService.getContainerLogs("abc123")).thenReturn("App failed to become healthy");
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNotNull();
        assertThat(deployment.getImageName()).isEqualTo(imageName);
        assertThat(deployment.getContainerId()).isEqualTo("abc123");
        assertThat(deployment.getHostPort()).isEqualTo(49152);
        assertThat(deployment.getContainerPort()).isEqualTo(8080);
        assertThat(deployment.getDeploymentUrl()).isEqualTo("http://localhost:49152");

        verify(deploymentRepository, times(3)).save(deployment);
        verify(healthCheckService).waitUntilHealthy("http://localhost:49152", "/health");
        verify(dockerService).getContainerLogs("abc123");
        verify(dockerService).stopContainer("abc123");

        InOrder inOrder = inOrder(deploymentLogService, dockerService, deploymentRepository);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Running health check: http://localhost:49152/health");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Health check failed after 30 attempt(s)");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Collecting unhealthy container logs");
        inOrder.verify(dockerService).getContainerLogs("abc123");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "App failed to become healthy");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Stopping unhealthy container: abc123");
        inOrder.verify(dockerService).stopContainer("abc123");
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Unhealthy container stopped: abc123");
        inOrder.verify(deploymentRepository).save(deployment);
        inOrder.verify(deploymentLogService).appendLog(deploymentId, "Deployment failed");
    }

    @Test
    void execute_stillMarksDeploymentFailedWhenUnhealthyContainerCleanupFails() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        Project project = project();
        String imageName = "vibeboot-vibe-boot:" + deploymentId;
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(1);
        when(projectService.getProjectOrThrow(deployment.getProjectId())).thenReturn(project);
        when(dockerService.buildImage(deploymentId, project, PROJECT_DIRECTORY))
                .thenReturn(new DockerBuildResult(imageName, ""));
        when(portAllocator.allocatePort()).thenReturn(49152);
        when(dockerService.runContainer(deploymentId, project, imageName, 49152))
                .thenReturn(new DockerRunResult("abc123", 49152, 8080, "http://localhost:49152"));
        when(healthCheckService.waitUntilHealthy("http://localhost:49152", "/health"))
                .thenReturn(new HealthCheckResult(
                        false,
                        URI.create("http://localhost:49152/health"),
                        30,
                        "Health check timed out"
                ));
        when(dockerService.getContainerLogs("abc123"))
                .thenThrow(new DockerServiceException("Docker container logs failed: no such container", "logs stderr"));
        doThrow(new DockerServiceException("Docker container stop failed: no such container", "stop stderr"))
                .when(dockerService).stopContainer("abc123");
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getFinishedAt()).isNotNull();

        verify(dockerService).getContainerLogs("abc123");
        verify(dockerService).stopContainer("abc123");
        verify(deploymentRepository, times(3)).save(deployment);
        verify(deploymentLogService).appendLog(deploymentId, "logs stderr");
        verify(deploymentLogService).appendLog(
                deploymentId,
                "Could not collect unhealthy container logs: Docker container logs failed: no such container"
        );
        verify(deploymentLogService).appendLog(deploymentId, "stop stderr");
        verify(deploymentLogService).appendLog(
                deploymentId,
                "Could not stop unhealthy container: Docker container stop failed: no such container"
        );
        verify(deploymentLogService).appendLog(deploymentId, "Deployment failed");
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

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository, never()).markRunningIfQueued(
                any(UUID.class),
                any(Instant.class),
                any(DeploymentStatus.class),
                any(DeploymentStatus.class)
        );
        verify(projectService, never()).getProjectOrThrow(any(UUID.class));
        verify(dockerService, never()).buildImage(any(UUID.class), any(Project.class), any(Path.class));
        verify(portAllocator, never()).allocatePort();
        verify(dockerService, never()).runContainer(any(UUID.class), any(Project.class), any(String.class), anyInt());
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());
        verify(deploymentRepository, never()).save(any(Deployment.class));
        verify(deploymentLogService, never()).appendLog(any(UUID.class), any(String.class));
    }

    @Test
    void execute_skipsDeploymentThatAlreadyFinished() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        deployment.markFinished(DeploymentStatus.SUCCESS);
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository, never()).markRunningIfQueued(
                any(UUID.class),
                any(Instant.class),
                any(DeploymentStatus.class),
                any(DeploymentStatus.class)
        );
        verify(projectService, never()).getProjectOrThrow(any(UUID.class));
        verify(dockerService, never()).buildImage(any(UUID.class), any(Project.class), any(Path.class));
        verify(portAllocator, never()).allocatePort();
        verify(dockerService, never()).runContainer(any(UUID.class), any(Project.class), any(String.class), anyInt());
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());
        verify(deploymentRepository, never()).save(any(Deployment.class));
        verify(deploymentLogService, never()).appendLog(any(UUID.class), any(String.class));
    }

    @Test
    void execute_skipsWhenAnotherConsumerAlreadyStartedDeployment() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID());
        DeploymentExecutor deploymentExecutor = deploymentExecutor();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        )).thenReturn(0);

        deploymentExecutor.execute(deploymentId);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.QUEUED);

        verify(deploymentRepository).findById(deploymentId);
        verify(deploymentRepository).markRunningIfQueued(
                eq(deploymentId),
                any(Instant.class),
                eq(DeploymentStatus.QUEUED),
                eq(DeploymentStatus.RUNNING)
        );
        verify(projectService, never()).getProjectOrThrow(any(UUID.class));
        verify(dockerService, never()).buildImage(any(UUID.class), any(Project.class), any(Path.class));
        verify(portAllocator, never()).allocatePort();
        verify(dockerService, never()).runContainer(any(UUID.class), any(Project.class), any(String.class), anyInt());
        verify(healthCheckService, never()).waitUntilHealthy(anyString(), anyString());
        verify(deploymentRepository, never()).save(any(Deployment.class));
        verify(deploymentLogService, never()).appendLog(any(UUID.class), any(String.class));
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

    private DeploymentExecutor deploymentExecutor() {
        return new DeploymentExecutor(
                deploymentRepository,
                deploymentLogService,
                projectService,
                dockerService,
                portAllocator,
                healthCheckService
        );
    }

    private Project project() {
        return new Project(
                "Vibe Boot",
                "https://github.com/alexeisoki/vibe-boot",
                "main",
                null,
                PROJECT_DIRECTORY.toString(),
                "Dockerfile",
                8080,
                "/health"
        );
    }
}
