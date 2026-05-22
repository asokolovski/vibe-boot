package com.alexeisoki.vibeboot.deployment.queue;

import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.config.RabbitMqConfig;
import com.alexeisoki.vibeboot.deployment.DeploymentExecutor;

@Component
public class DeploymentQueueConsumer {

    private final DeploymentExecutor deploymentExecutor;

    public DeploymentQueueConsumer(DeploymentExecutor deploymentExecutor) {
        this.deploymentExecutor = deploymentExecutor;
    }

    @RabbitListener(
            queues = RabbitMqConfig.DEPLOYMENT_REQUESTED_QUEUE,
            concurrency = "2-4"
    )
    public void consumeDeploymentRequested(String deploymentId) {
        deploymentExecutor.execute(UUID.fromString(deploymentId));
    }
}
