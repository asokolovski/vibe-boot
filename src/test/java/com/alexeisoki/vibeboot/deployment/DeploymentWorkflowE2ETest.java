package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.alexeisoki.vibeboot.deployment.dto.DeploymentLogResponse;
import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
import com.alexeisoki.vibeboot.deployment.runtime.DockerBuildResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerRunResult;
import com.alexeisoki.vibeboot.deployment.runtime.DockerService;
import com.alexeisoki.vibeboot.deployment.runtime.GitCloneResult;
import com.alexeisoki.vibeboot.deployment.runtime.GitService;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckResult;
import com.alexeisoki.vibeboot.deployment.runtime.HealthCheckService;
import com.alexeisoki.vibeboot.deployment.runtime.PortAllocator;
import com.alexeisoki.vibeboot.deployment.runtime.WorkspaceService;
import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectEnvironmentVariableService;
import com.alexeisoki.vibeboot.project.ProjectService;
import com.alexeisoki.vibeboot.project.dto.AddProjectEnvironmentVariableRequest;
import com.alexeisoki.vibeboot.project.dto.CreateProjectRequest;
import com.alexeisoki.vibeboot.project.dto.ProjectResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class DeploymentWorkflowE2ETest {

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<DeploymentResponse>> DEPLOYMENT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<DeploymentLogResponse>> DEPLOYMENT_LOG_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestTestClient restTestClient;

    @Autowired
    private DockerService dockerService;

    @Autowired
    DeploymentWorkflowE2ETest(RestTestClient restTestClient) {
        this.restTestClient = restTestClient;
    }

    @Test
    void deploymentWorkflow_runsEndToEndOverHttp() {
        ProjectResponse project = createProject("vibe-payment-api");
        addEnvVar(project.id());

        List<ProjectResponse> projects = restTestClient.get()
                .uri("/api/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PROJECT_LIST)
                .returnResult()
                .getResponseBody();

        assertThat(projects)
                .isNotNull()
                .extracting(ProjectResponse::id)
                .contains(project.id());

        DeploymentResponse queuedDeployment = restTestClient.post()
                .uri("/api/deployments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TriggerDeploymentRequest(project.id()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DeploymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(queuedDeployment).isNotNull();
        assertThat(queuedDeployment.projectId()).isEqualTo(project.id());
        assertThat(queuedDeployment.status()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(queuedDeployment.createdAt()).isNotNull();
        assertThat(queuedDeployment.startedAt()).isNull();
        assertThat(queuedDeployment.finishedAt()).isNull();
        assertThat(queuedDeployment.imageName()).isNull();
        assertThat(queuedDeployment.containerId()).isNull();
        assertThat(queuedDeployment.hostPort()).isNull();
        assertThat(queuedDeployment.containerPort()).isNull();
        assertThat(queuedDeployment.deploymentUrl()).isNull();

        String expectedImageName = "vibeboot-" + project.name() + ":" + queuedDeployment.id();
        String expectedContainerId = "container-" + queuedDeployment.id();

        await()
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(10))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    DeploymentResponse deployment = getDeployment(queuedDeployment);

                    assertThat(deployment.status()).isEqualTo(DeploymentStatus.RUNNING);
                    assertThat(deployment.startedAt()).isNotNull();
                    assertThat(deployment.finishedAt()).isNull();
                });

        await()
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(20))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    DeploymentResponse deployment = getDeployment(queuedDeployment);

                    assertThat(deployment.status()).isEqualTo(DeploymentStatus.SUCCESS);
                    assertThat(deployment.startedAt()).isNotNull();
                    assertThat(deployment.finishedAt()).isNotNull();
                    assertThat(deployment.imageName()).isEqualTo(expectedImageName);
                    assertThat(deployment.containerId()).isEqualTo(expectedContainerId);
                    assertThat(deployment.hostPort()).isEqualTo(49152);
                    assertThat(deployment.containerPort()).isEqualTo(8080);
                    assertThat(deployment.deploymentUrl()).isEqualTo("http://localhost:49152");
                });

        verify(dockerService).runContainer(
                eq(queuedDeployment.id()),
                any(Project.class),
                eq(expectedImageName),
                eq(49152),
                eq(Map.of("API_KEY", "secret"))
        );

        List<DeploymentResponse> deploymentHistory = restTestClient.get()
                .uri("/api/projects/{projectId}/deployments", project.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DEPLOYMENT_LIST)
                .returnResult()
                .getResponseBody();

        assertThat(deploymentHistory)
                .isNotNull()
                .hasSize(1);
        assertThat(deploymentHistory.get(0).id()).isEqualTo(queuedDeployment.id());
        assertThat(deploymentHistory.get(0).status()).isEqualTo(DeploymentStatus.SUCCESS);

        await()
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(20))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    List<DeploymentLogResponse> logs = getDeploymentLogs(queuedDeployment);

                    assertThat(logs)
                            .extracting(DeploymentLogResponse::message)
                            .containsExactly(
                                    "Deployment started",
                                    "Created workspace",
                                    "Cloning repository",
                                    "clone ok",
                                    "Repository cloned successfully",
                                    "Building Docker image",
                                    "build ok",
                                    "Docker image built: " + expectedImageName,
                                    "Loading project environment variables",
                                    "Loaded 1 project environment variable(s)",
                                    "Starting Docker container",
                                    "Docker container started: " + expectedContainerId,
                                    "Deployment URL: http://localhost:49152",
                                    "Running health check: http://localhost:49152/health",
                                    "Health check succeeded after 1 attempt(s)",
                                    "Deployment succeeded",
                                    "Workspace cleaned up"
                            );
                });

        DeploymentResponse stoppedDeployment = restTestClient.post()
                .uri("/api/deployments/{deploymentId}/stop", queuedDeployment.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeploymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(stoppedDeployment).isNotNull();
        assertThat(stoppedDeployment.status()).isEqualTo(DeploymentStatus.STOPPED);
        assertThat(stoppedDeployment.containerId()).isEqualTo(expectedContainerId);
        assertThat(stoppedDeployment.deploymentUrl()).isEqualTo("http://localhost:49152");
        assertThat(stoppedDeployment.finishedAt()).isNotNull();

        verify(dockerService).stopContainer(expectedContainerId);

        List<DeploymentLogResponse> stoppedLogs = getDeploymentLogs(queuedDeployment);
        assertThat(stoppedLogs)
                .extracting(DeploymentLogResponse::message)
                .contains(
                        "Docker container stopped: " + expectedContainerId,
                        "Deployment stopped"
                );
    }

    @Test
    void deploymentHistory_returnsEmptyListForProjectWithNoDeployments() {
        ProjectResponse project = createProject("quiet-api");

        List<DeploymentResponse> deploymentHistory = restTestClient.get()
                .uri("/api/projects/{projectId}/deployments", project.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DEPLOYMENT_LIST)
                .returnResult()
                .getResponseBody();

        assertThat(deploymentHistory)
                .isNotNull()
                .isEmpty();
    }

    private ProjectResponse createProject(String name) {
        ProjectResponse project = restTestClient.post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateProjectRequest(name, "https://github.com/alexeisoki/" + name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(project).isNotNull();
        assertThat(project.id()).isNotNull();
        assertThat(project.name()).isEqualTo(name);
        assertThat(project.branch()).isEqualTo("main");
        assertThat(project.dockerfilePath()).isEqualTo("Dockerfile");
        assertThat(project.containerPort()).isEqualTo(8080);
        assertThat(project.healthCheckPath()).isEqualTo("/health");
        assertThat(project.createdAt()).isNotNull();
        return project;
    }

    private void addEnvVar(UUID projectId) {
        restTestClient.post()
                .uri("/api/projects/{projectId}/env", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AddProjectEnvironmentVariableRequest("API_KEY", "secret"))
                .exchange()
                .expectStatus().isCreated();
    }

    private DeploymentResponse getDeployment(DeploymentResponse deployment) {
        DeploymentResponse response = restTestClient.get()
                .uri("/api/deployments/{deploymentId}", deployment.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeploymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        return response;
    }

    private List<DeploymentLogResponse> getDeploymentLogs(DeploymentResponse deployment) {
        List<DeploymentLogResponse> response = restTestClient.get()
                .uri("/api/deployments/{deploymentId}/logs", deployment.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DEPLOYMENT_LOG_LIST)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        return response;
    }

    @TestConfiguration
    static class DeterministicDeploymentQueueConfig {

        @Bean
        @Primary
        DeploymentExecutor deterministicDeploymentExecutor(
                DeploymentRepository deploymentRepository,
                DeploymentLogService deploymentLogService,
                ProjectService projectService,
                DockerService dockerService,
                PortAllocator portAllocator,
                HealthCheckService healthCheckService,
                WorkspaceService workspaceService,
                GitService gitService,
                ProjectEnvironmentVariableService environmentVariableService
        ) {
            return new DeploymentExecutor(
                    deploymentRepository,
                    deploymentLogService,
                    projectService,
                    dockerService,
                    portAllocator,
                    healthCheckService,
                    workspaceService,
                    gitService,
                    environmentVariableService
            );
        }

        @Bean
        @Primary
        WorkspaceService deterministicWorkspaceService() {
            WorkspaceService workspaceService = mock(WorkspaceService.class);
            when(workspaceService.createWorkspace(any(UUID.class)))
                    .thenReturn(Path.of("/tmp/vibeboot-e2e-workspace"));
            return workspaceService;
        }

        @Bean
        @Primary
        GitService deterministicGitService() {
            GitService gitService = mock(GitService.class);
            when(gitService.cloneRepository(anyString(), anyString(), any(Path.class)))
                    .thenReturn(new GitCloneResult("clone ok"));
            return gitService;
        }

        @Bean
        @Primary
        DockerService deterministicDockerService() {
            DockerService dockerService = mock(DockerService.class);
            when(dockerService.buildImage(any(UUID.class), any(Project.class), any(Path.class)))
                    .thenAnswer(invocation -> {
                        UUID deploymentId = invocation.getArgument(0, UUID.class);
                        Project project = invocation.getArgument(1, Project.class);
                        return new DockerBuildResult("vibeboot-" + project.getName() + ":" + deploymentId, "build ok");
                    });
            when(dockerService.runContainer(any(UUID.class), any(Project.class), anyString(), anyInt(), anyMap()))
                    .thenAnswer(invocation -> {
                        UUID deploymentId = invocation.getArgument(0, UUID.class);
                        int hostPort = invocation.getArgument(3, Integer.class);
                        return new DockerRunResult(
                                "container-" + deploymentId,
                                hostPort,
                                8080,
                                "http://localhost:" + hostPort
                        );
                    });
            return dockerService;
        }

        @Bean
        @Primary
        PortAllocator deterministicPortAllocator() {
            PortAllocator portAllocator = mock(PortAllocator.class);
            when(portAllocator.allocatePort()).thenReturn(49152);
            return portAllocator;
        }

        @Bean
        @Primary
        HealthCheckService deterministicHealthCheckService() {
            HealthCheckService healthCheckService = mock(HealthCheckService.class);
            when(healthCheckService.waitUntilHealthy(anyString(), anyString()))
                    .thenReturn(new HealthCheckResult(
                            true,
                            URI.create("http://localhost:49152/health"),
                            1,
                            "Health check succeeded"
                    ));
            return healthCheckService;
        }

        @Bean
        @Primary
        RabbitTemplate deterministicRabbitTemplate(DeploymentExecutor deploymentExecutor) {
            RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
            doAnswer(invocation -> {
                String deploymentId = invocation.getArgument(2, String.class);
                CompletableFuture.runAsync(() -> deploymentExecutor.execute(UUID.fromString(deploymentId)));
                return null;
            }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
            return rabbitTemplate;
        }
    }
}
