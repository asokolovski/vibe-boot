package com.alexeisoki.vibeboot.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alexeisoki.vibeboot.auth.GitHubUserProfile;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User findOrCreateFromGitHubProfile(GitHubUserProfile profile) {
        User user = userRepository.findByGithubId(profile.githubId())
                .orElseGet(() -> new User(
                        profile.githubId(),
                        profile.githubUsername(),
                        profile.name(),
                        profile.email(),
                        profile.avatarUrl()
                ));

        // GitHub usernames/profile fields can change, so refresh our local copy on each login.
        user.updateGitHubProfile(
                profile.githubUsername(),
                profile.name(),
                profile.email(),
                profile.avatarUrl()
        );

        return userRepository.save(user);
    }

    public User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
