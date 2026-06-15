package com.alexeisoki.vibeboot.auth.dto;

import java.util.UUID;

import com.alexeisoki.vibeboot.user.User;

public record CurrentUserResponse(
        boolean authenticated,
        UUID id,
        Long githubId,
        String githubUsername,
        String name,
        String email,
        String avatarUrl
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                true,
                user.getId(),
                user.getGithubId(),
                user.getGithubUsername(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl()
        );
    }
}
