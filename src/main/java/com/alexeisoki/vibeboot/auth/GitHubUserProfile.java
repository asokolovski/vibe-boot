package com.alexeisoki.vibeboot.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubUserProfile(
        @JsonProperty("id") Long githubId,
        @JsonProperty("login") String githubUsername,
        String name,
        String email,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
