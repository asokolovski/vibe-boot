package com.alexeisoki.vibeboot.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexeisoki.vibeboot.auth.GitHubUserProfile;
import com.alexeisoki.vibeboot.shared.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void findOrCreateFromGitHubProfile_createsUserWhenGitHubIdIsNew() {
        UserService userService = new UserService(userRepository);
        GitHubUserProfile profile = new GitHubUserProfile(
                12345L,
                "alexei",
                "Alexei",
                "alexei@example.com",
                "https://avatars.githubusercontent.com/u/12345"
        );
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        when(userRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.findOrCreateFromGitHubProfile(profile);

        assertThat(user).isSameAs(userCaptor.getValue());
        assertThat(user.getGithubId()).isEqualTo(12345L);
        assertThat(user.getGithubUsername()).isEqualTo("alexei");
        assertThat(user.getName()).isEqualTo("Alexei");
        assertThat(user.getEmail()).isEqualTo("alexei@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345");
        verify(userRepository).findByGithubId(12345L);
    }

    @Test
    void findOrCreateFromGitHubProfile_updatesExistingUserProfileFields() {
        UserService userService = new UserService(userRepository);
        User existingUser = new User(12345L, "old-login", null, null, null);
        GitHubUserProfile profile = new GitHubUserProfile(
                12345L,
                "new-login",
                "New Name",
                "new@example.com",
                "https://avatars.githubusercontent.com/u/12345?v=2"
        );

        when(userRepository.findByGithubId(12345L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User user = userService.findOrCreateFromGitHubProfile(profile);

        assertThat(user).isSameAs(existingUser);
        assertThat(user.getGithubUsername()).isEqualTo("new-login");
        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345?v=2");
        verify(userRepository).save(existingUser);
    }

    @Test
    void getUserOrThrow_returnsUserWhenItExists() {
        UserService userService = new UserService(userRepository);
        UUID userId = UUID.randomUUID();
        User user = new User(12345L, "alexei", null, null, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(userService.getUserOrThrow(userId)).isSameAs(user);
    }

    @Test
    void getUserOrThrow_throwsWhenUserDoesNotExist() {
        UserService userService = new UserService(userRepository);
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserOrThrow(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }
}
