package com.alexeisoki.vibeboot.deployment;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Service
public class DeploymentWorker {

    private final DeploymentRepository deploymentRepository;
    private final BooleanSupplier successChooser;
    private final Duration simulatedDeploymentTime;

    @Autowired
    public DeploymentWorker(DeploymentRepository deploymentRepository) {
        this(
                deploymentRepository,
                () -> ThreadLocalRandom.current().nextBoolean(),
                Duration.ofSeconds(5)
        );
    }

    DeploymentWorker(
            DeploymentRepository deploymentRepository,
            BooleanSupplier successChooser,
            Duration simulatedDeploymentTime
    ) {
        this.deploymentRepository = deploymentRepository;
        this.successChooser = successChooser;
        this.simulatedDeploymentTime = simulatedDeploymentTime;
    }

    @Async("deploymentTaskExecutor")
    public void runDeployment(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found"));

        deployment.markRunning();
        deploymentRepository.save(deployment);

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
            throw new IllegalStateException("Deployment worker was interrupted", exception);
        }
    }
}
