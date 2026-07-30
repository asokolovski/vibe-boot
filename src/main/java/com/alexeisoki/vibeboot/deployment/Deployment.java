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
    private String imageTag;

    @Column(nullable = true)
    private String containerId;

    @Column(nullable = true)
    private Integer hostPort;

    @Column(nullable = true)
    private Integer containerPort;

    @Column(nullable = true)
    private String deploymentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private DeploymentRuntimeType runtimeType;

    @Column(nullable = true)
    private String composeProjectName;

    @Column(nullable = true)
    private String primaryServiceName;

    @Column(nullable = true)
    private Integer attemptCount = 0;

    protected Deployment() {
    }

    public Deployment(UUID projectId) {
        this(projectId, null);
    }

    public Deployment(UUID projectId, String imageTag) {
        this.projectId = projectId;
        this.status = DeploymentStatus.QUEUED;
        this.imageTag = blankToNull(imageTag);
    }

    public void markRunning() {
        markRunning(Instant.now());
    }

    public void markRunning(Instant startedAt) {
        status = DeploymentStatus.RUNNING;
        this.startedAt = startedAt;
        this.finishedAt = null;
        this.attemptCount = getAttemptCount() + 1;
    }

    public void markFinished(DeploymentStatus finishedStatus) {
        if (finishedStatus != DeploymentStatus.SUCCESS && finishedStatus != DeploymentStatus.FAILED) {
            throw new IllegalArgumentException("Finished status must be SUCCESS or FAILED");
        }

        status = finishedStatus;
        finishedAt = Instant.now();
    }

    public void markStopped() {
        status = DeploymentStatus.STOPPED;
    }

    public void prepareForRetry() {
        status = DeploymentStatus.QUEUED;
        startedAt = null;
        finishedAt = null;
        imageName = null;
        containerId = null;
        hostPort = null;
        containerPort = null;
        deploymentUrl = null;
        runtimeType = null;
        composeProjectName = null;
        primaryServiceName = null;
    }

    public void recordDockerRuntime(
            String imageName,
            String containerId,
            Integer hostPort,
            Integer containerPort,
            String deploymentUrl
    ) {
        this.runtimeType = DeploymentRuntimeType.SINGLE_CONTAINER;
        this.imageName = imageName;
        this.containerId = containerId;
        this.hostPort = hostPort;
        this.containerPort = containerPort;
        this.deploymentUrl = deploymentUrl;
    }

    public void recordComposeRuntime(
            String composeProjectName,
            String primaryServiceName,
            Integer hostPort,
            Integer containerPort,
            String deploymentUrl
    ) {
        this.runtimeType = DeploymentRuntimeType.DOCKER_COMPOSE;
        this.composeProjectName = composeProjectName;
        this.primaryServiceName = primaryServiceName;
        this.hostPort = hostPort;
        this.containerPort = containerPort;
        this.deploymentUrl = deploymentUrl;
    }

    public void recordDockerImage(String imageName) {
        this.imageName = imageName;
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

    public String getImageTag() {
        return imageTag;
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

    public DeploymentRuntimeType getRuntimeType() {
        return runtimeType != null ? runtimeType : DeploymentRuntimeType.SINGLE_CONTAINER;
    }

    public String getComposeProjectName() {
        return composeProjectName;
    }

    public String getPrimaryServiceName() {
        return primaryServiceName;
    }

    public int getAttemptCount() {
        return attemptCount == null ? 0 : attemptCount;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
