package com.alexeisoki.vibeboot.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
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

import com.alexeisoki.vibeboot.deployment.dto.DeploymentResponse;
import com.alexeisoki.vibeboot.deployment.dto.TriggerDeploymentRequest;
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

    private final RestTestClient restTestClient;

    @Autowired
    DeploymentWorkflowE2ETest(RestTestClient restTestClient) {
        this.restTestClient = restTestClient;
    }

    @Test
    void deploymentWorkflow_runsEndToEndOverHttp() {
        ProjectResponse project = createProject("vibe-payment-api");

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
                });

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
                .body(new CreateProjectRequest(
                        name,
                        "https://github.com/alexeisoki/" + name,
                        "main",
                        "./gradlew bootRun"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(project).isNotNull();
        assertThat(project.id()).isNotNull();
        assertThat(project.name()).isEqualTo(name);
        assertThat(project.createdAt()).isNotNull();
        return project;
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

    @TestConfiguration
    static class DeterministicDeploymentQueueConfig {

        @Bean
        @Primary
        DeploymentExecutor deterministicDeploymentExecutor(DeploymentRepository deploymentRepository) {
            return new DeploymentExecutor(
                    deploymentRepository,
                    () -> true,
                    Duration.ofMillis(300)
            );
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
