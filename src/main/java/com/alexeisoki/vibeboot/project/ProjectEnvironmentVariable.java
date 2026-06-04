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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
        name = "project_environment_variables",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "env_key"})
)
public class ProjectEnvironmentVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @NotBlank
    @Column(name = "env_key", nullable = false)
    private String key;

    @NotBlank
    @Column(nullable = false, length = 4096)
    private String valueEncrypted;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectEnvironmentVariable() {
    }

    public ProjectEnvironmentVariable(UUID projectId, String key, String valueEncrypted) {
        this.projectId = projectId;
        this.key = key;
        this.valueEncrypted = valueEncrypted;
    }

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

    public String getKey() {
        return key;
    }

    public String getValueEncrypted() {
        return valueEncrypted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
