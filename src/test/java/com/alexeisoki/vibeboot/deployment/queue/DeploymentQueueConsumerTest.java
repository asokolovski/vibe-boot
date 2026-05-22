package com.alexeisoki.vibeboot.deployment.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexeisoki.vibeboot.config.RabbitMqConfig;
import com.alexeisoki.vibeboot.deployment.DeploymentExecutor;

@ExtendWith(MockitoExtension.class)
class DeploymentQueueConsumerTest {

    @Mock
    private DeploymentExecutor deploymentExecutor;

    @Test
    void consumeDeploymentRequested_parsesDeploymentIdAndCallsExecutor() {
        DeploymentQueueConsumer deploymentQueueConsumer = new DeploymentQueueConsumer(deploymentExecutor);
        UUID deploymentId = UUID.randomUUID();

        deploymentQueueConsumer.consumeDeploymentRequested(deploymentId.toString());

        verify(deploymentExecutor).execute(deploymentId);
    }

    @Test
    @DisplayName("consumeDeploymentRequested listens to the deployment queue with listener concurrency")
    void consumeDeploymentRequested_hasRabbitListenerConfiguration() throws NoSuchMethodException {
        Method method = DeploymentQueueConsumer.class.getMethod("consumeDeploymentRequested", String.class);

        RabbitListener rabbitListener = method.getAnnotation(RabbitListener.class);

        assertThat(rabbitListener).isNotNull();
        assertThat(rabbitListener.queues()).containsExactly(RabbitMqConfig.DEPLOYMENT_REQUESTED_QUEUE);
        assertThat(rabbitListener.concurrency()).isEqualTo("2-4");
    }
}
