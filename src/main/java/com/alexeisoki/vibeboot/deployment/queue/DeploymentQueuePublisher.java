package com.alexeisoki.vibeboot.deployment.queue;

import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.config.RabbitMqConfig;

@Component
public class DeploymentQueuePublisher {

    private final RabbitTemplate rabbitTemplate;

    public DeploymentQueuePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishDeploymentRequested(UUID deploymentId) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DEPLOYMENT_EXCHANGE,
                RabbitMqConfig.DEPLOYMENT_REQUESTED_ROUTING_KEY,
                deploymentId.toString()
        );
    }
}
