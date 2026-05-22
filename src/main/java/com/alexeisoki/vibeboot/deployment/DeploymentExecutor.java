package com.alexeisoki.vibeboot.deployment;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Component
public class DeploymentExecutor {

    private final DeploymentRepository deploymentRepository;
    private final BooleanSupplier successChooser;
    private final Duration simulatedDeploymentTime;

    @Autowired
    public DeploymentExecutor(DeploymentRepository deploymentRepository) {
        this(
                deploymentRepository,
                () -> ThreadLocalRandom.current().nextBoolean(),
                Duration.ofSeconds(5)
        );
    }

    DeploymentExecutor(
            DeploymentRepository deploymentRepository,
            BooleanSupplier successChooser,
            Duration simulatedDeploymentTime
    ) {
        this.deploymentRepository = deploymentRepository;
        this.successChooser = successChooser;
        this.simulatedDeploymentTime = simulatedDeploymentTime;
    }

    public void execute(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found"));

        if (deployment.getStatus() != DeploymentStatus.QUEUED) {
            return;
        }

        Instant startedAt = Instant.now();
        int startedDeployments = deploymentRepository.markRunningIfQueued(
                deploymentId,
                startedAt,
                DeploymentStatus.QUEUED,
                DeploymentStatus.RUNNING
        );

        if (startedDeployments == 0) {
            return;
        }

        deployment.markRunning(startedAt);

        sleep();

        DeploymentStatus finishedStatus = successChooser.getAsBoolean()
                ? DeploymentStatus.SUCCESS
                : DeploymentStatus.FAILED;
        deployment.markFinished(finishedStatus);
        deploymentRepository.save(deployment);
    }

    private void sleep() {
        try {
            Thread.sleep(simulatedDeploymentTime.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Deployment executor was interrupted", exception);
        }
    }
}
