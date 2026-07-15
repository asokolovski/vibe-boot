package com.alexeisoki.vibeboot.project;

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
import jakarta.validation.constraints.NotBlank;

// @Entity tells JPA/Hibernate that this class should be stored in a database table.
@Entity
// @Table controls the actual table name instead of relying on the default class name.
@Table(name = "projects")
public class Project {
    public static final String DEFAULT_BRANCH = "main";
    public static final String DEFAULT_DOCKERFILE_PATH = "Dockerfile";
    public static final String DEFAULT_COMPOSE_FILE_PATH = "compose.yaml";
    public static final int DEFAULT_CONTAINER_PORT = 8080;
    public static final String DEFAULT_HEALTH_CHECK_PATH = "/health";

    // @Id marks the primary key, and @GeneratedValue lets Hibernate create UUIDs for new rows.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // @NotBlank is validation; @Column is JPA/database mapping.
    @NotBlank
    @Column(nullable = false)
    private String name;

    // Source-built projects use this. Registry-backed projects leave it null.
    @Column(nullable = true)
    private String repositoryUrl;

    @NotBlank
    @Column(nullable = false)
    private String branch;

    @Column(nullable = true)
    private String dockerfilePath = DEFAULT_DOCKERFILE_PATH;

    @Column(nullable = true)
    private Integer containerPort = DEFAULT_CONTAINER_PORT;

    @Column(nullable = true)
    private String healthCheckPath = DEFAULT_HEALTH_CHECK_PATH;

    @Column(name = "owner_user_id", nullable = true)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ProjectSourceType sourceType;

    @Column(nullable = true)
    private String containerRegistry;

    @Column(nullable = true)
    private String composeFilePath = DEFAULT_COMPOSE_FILE_PATH;

    @Column(nullable = true)
    private String primaryServiceName;

    // Stored by JPA, but only set once when the entity is first inserted.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(String name, String repositoryUrl, String branch) {
        this(name, repositoryUrl, branch, null, null, null);
    }

    public Project(
            String name,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath
    ) {
        this(name, repositoryUrl, branch, dockerfilePath, containerPort, healthCheckPath, null);
    }

    public Project(
            String name,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath,
            UUID ownerUserId
    ) {
        this(name, repositoryUrl, branch, dockerfilePath, containerPort, healthCheckPath, ownerUserId, null, null);
    }

    public Project(
            String name,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath,
            UUID ownerUserId,
            ProjectSourceType sourceType,
            String containerRegistry
    ) {
        this(name, repositoryUrl, branch, dockerfilePath, containerPort, healthCheckPath, ownerUserId,
                sourceType, containerRegistry, null, null);
    }

    public Project(
            String name,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath,
            UUID ownerUserId,
            ProjectSourceType sourceType,
            String containerRegistry,
            String composeFilePath,
            String primaryServiceName
    ) {
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.branch = defaultIfBlank(branch, DEFAULT_BRANCH);
        this.dockerfilePath = defaultIfBlank(dockerfilePath, DEFAULT_DOCKERFILE_PATH);
        this.containerPort = containerPort != null ? containerPort : defaultContainerPort(sourceType);
        this.healthCheckPath = defaultIfBlank(healthCheckPath, DEFAULT_HEALTH_CHECK_PATH);
        this.ownerUserId = ownerUserId;
        this.sourceType = sourceType;
        this.containerRegistry = containerRegistry;
        this.composeFilePath = defaultIfBlank(composeFilePath, DEFAULT_COMPOSE_FILE_PATH);
        this.primaryServiceName = blankToNull(primaryServiceName);
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

    public String getName() {
        return name;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getBranch() {
        return defaultIfBlank(branch, DEFAULT_BRANCH);
    }

    public String getDockerfilePath() {
        return defaultIfBlank(dockerfilePath, DEFAULT_DOCKERFILE_PATH);
    }

    public Integer getContainerPort() {
        if (getSourceType() == ProjectSourceType.DOCKER_COMPOSE) {
            return containerPort;
        }

        return containerPort != null ? containerPort : DEFAULT_CONTAINER_PORT;
    }

    public String getHealthCheckPath() {
        return defaultIfBlank(healthCheckPath, DEFAULT_HEALTH_CHECK_PATH);
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ProjectSourceType getSourceType() {
        return sourceType != null ? sourceType : ProjectSourceType.GITHUB_REPOSITORY;
    }

    public String getContainerRegistry() {
        return containerRegistry;
    }

    public String getComposeFilePath() {
        return defaultIfBlank(composeFilePath, DEFAULT_COMPOSE_FILE_PATH);
    }

    public String getPrimaryServiceName() {
        return primaryServiceName;
    }

    private Integer defaultContainerPort(ProjectSourceType sourceType) {
        return sourceType == ProjectSourceType.DOCKER_COMPOSE ? null : DEFAULT_CONTAINER_PORT;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

}
