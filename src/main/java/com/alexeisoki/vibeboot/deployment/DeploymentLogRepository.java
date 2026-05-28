package com.alexeisoki.vibeboot.deployment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, UUID> {
    List<DeploymentLog> findByDeploymentIdOrderByCreatedAtAsc(UUID deploymentId);
}
