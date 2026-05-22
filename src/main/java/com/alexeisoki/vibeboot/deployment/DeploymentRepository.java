package com.alexeisoki.vibeboot.deployment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
    List<Deployment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Deployment deployment
            set deployment.status = :runningStatus,
                deployment.startedAt = :startedAt
            where deployment.id = :deploymentId
              and deployment.status = :queuedStatus
            """)
    int markRunningIfQueued(
            UUID deploymentId,
            Instant startedAt,
            DeploymentStatus queuedStatus,
            DeploymentStatus runningStatus
    );
}
