package com.alexeisoki.vibeboot.deployment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

// @Entity tells JPA/Hibernate that this class should be stored in a database table.
@Entity
// @Table controls the actual table name instead of relying on the default class name.
@Table(name = "deployments")
public class Deployment {

    // @Id marks the primary key, and @GeneratedValue lets Hibernate create UUIDs for new rows.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(nullable = false)
    private UUID projectId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    // Stored by JPA, but only set once when the entity is first inserted.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = true)
    private Instant startedAt;

    @Column(nullable = true)    
    private Instant finishedAt;

    @Column(nullable = true)
    private String imageName;

    @Column(nullable = true)
    private String containerId;

    @Column(nullable = true)
    private Integer hostPort;

    @Column(nullable = true)
    private Integer containerPort;

    @Column(nullable = true)
    private String deploymentUrl;

    protected Deployment() {
    }

    public Deployment(UUID projectId) {
        this.projectId = projectId;
        this.status = DeploymentStatus.QUEUED;
    }

    public void markRunning() {
        markRunning(Instant.now());
    }

    public void markRunning(Instant startedAt) {
        status = DeploymentStatus.RUNNING;
        this.startedAt = startedAt;
    }

    public void markFinished(DeploymentStatus finishedStatus) {
        if (finishedStatus != DeploymentStatus.SUCCESS && finishedStatus != DeploymentStatus.FAILED) {
            throw new IllegalArgumentException("Finished status must be SUCCESS or FAILED");
        }

        status = finishedStatus;
        finishedAt = Instant.now();
    }

    public void recordDockerRuntime(
            String imageName,
            String containerId,
            Integer hostPort,
            Integer containerPort,
            String deploymentUrl
    ) {
        this.imageName = imageName;
        this.containerId = containerId;
        this.hostPort = hostPort;
        this.containerPort = containerPort;
        this.deploymentUrl = deploymentUrl;
    }

    // @PrePersist runs right before JPA inserts this entity into the database.
    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getImageName() {
        return imageName;
    }

    public String getContainerId() {
        return containerId;
    }

    public Integer getHostPort() {
        return hostPort;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public String getDeploymentUrl() {
        return deploymentUrl;
    }
}
