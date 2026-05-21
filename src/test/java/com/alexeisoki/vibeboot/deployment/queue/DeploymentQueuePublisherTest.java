package com.alexeisoki.vibeboot.deployment.queue;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.alexeisoki.vibeboot.config.RabbitMqConfig;

@ExtendWith(MockitoExtension.class)
class DeploymentQueuePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishDeploymentRequested_sendsDeploymentIdToDeploymentExchange() {
        DeploymentQueuePublisher deploymentQueuePublisher = new DeploymentQueuePublisher(rabbitTemplate);
        UUID deploymentId = UUID.randomUUID();

        deploymentQueuePublisher.publishDeploymentRequested(deploymentId);

        verify(rabbitTemplate).convertAndSend(
                RabbitMqConfig.DEPLOYMENT_EXCHANGE,
                RabbitMqConfig.DEPLOYMENT_REQUESTED_ROUTING_KEY,
                deploymentId.toString()
        );
    }
}
