package com.alexeisoki.vibeboot.user;

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
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // GitHub gives us this stable numeric id. Usernames can change, so this is our lookup key.
    @NotNull
    @Column(nullable = false, unique = true)
    private Long githubId;

    @NotBlank
    @Column(nullable = false)
    private String githubUsername;

    @Column
    private String name;

    @Column
    private String email;

    @Column
    private String avatarUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(Long githubId, String githubUsername, String name, String email, String avatarUrl) {
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void updateGitHubProfile(String githubUsername, String name, String email, String avatarUrl) {
        this.githubUsername = githubUsername;
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public UUID getId() {
        return id;
    }

    public Long getGithubId() {
        return githubId;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
