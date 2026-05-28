package com.alexeisoki.vibeboot.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    public static final String DEFAULT_DOCKERFILE_PATH = "Dockerfile";
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

    // Validation protects our Java/API boundary, while nullable = false protects the database.
    @NotBlank
    @Column(nullable = false)
    private String repositoryUrl;

    @NotBlank
    @Column(nullable = false)
    private String branch;

    @NotBlank
    @Column(nullable = false)
    private String runCommand;

    @Column(nullable = true)
    private String dockerfilePath = DEFAULT_DOCKERFILE_PATH;

    @Column(nullable = true)
    private Integer containerPort = DEFAULT_CONTAINER_PORT;

    @Column(nullable = true)
    private String healthCheckPath = DEFAULT_HEALTH_CHECK_PATH;

    // Stored by JPA, but only set once when the entity is first inserted.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(String name, String repositoryUrl, String branch, String runCommand) {
        this(name, repositoryUrl, branch, runCommand, null, null, null);
    }

    public Project(
            String name,
            String repositoryUrl,
            String branch,
            String runCommand,
            String dockerfilePath,
            Integer containerPort,
            String healthCheckPath
    ) {
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.branch = branch;
        this.runCommand = runCommand;
        this.dockerfilePath = defaultIfBlank(dockerfilePath, DEFAULT_DOCKERFILE_PATH);
        this.containerPort = containerPort != null ? containerPort : DEFAULT_CONTAINER_PORT;
        this.healthCheckPath = defaultIfBlank(healthCheckPath, DEFAULT_HEALTH_CHECK_PATH);
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
        return branch;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public String getDockerfilePath() {
        return defaultIfBlank(dockerfilePath, DEFAULT_DOCKERFILE_PATH);
    }

    public Integer getContainerPort() {
        return containerPort != null ? containerPort : DEFAULT_CONTAINER_PORT;
    }

    public String getHealthCheckPath() {
        return defaultIfBlank(healthCheckPath, DEFAULT_HEALTH_CHECK_PATH);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
