package com.alexeisoki.vibeboot.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void constructor_storesGitHubProfileFields() {
        User user = new User(
                12345L,
                "alexei",
                "Alexei",
                "alexei@example.com",
                "https://avatars.githubusercontent.com/u/12345"
        );

        assertThat(user.getGithubId()).isEqualTo(12345L);
        assertThat(user.getGithubUsername()).isEqualTo("alexei");
        assertThat(user.getName()).isEqualTo("Alexei");
        assertThat(user.getEmail()).isEqualTo("alexei@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345");
    }

    @Test
    void updateGitHubProfile_replacesMutableProfileFields() {
        User user = new User(12345L, "old-login", null, null, null);

        user.updateGitHubProfile(
                "new-login",
                "New Name",
                "new@example.com",
                "https://avatars.githubusercontent.com/u/12345?v=2"
        );

        assertThat(user.getGithubUsername()).isEqualTo("new-login");
        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345?v=2");
    }

    @Test
    void setCreatedAt_setsCreatedAtOnce() {
        User user = new User(12345L, "alexei", null, null, null);

        user.setCreatedAt();
        var createdAt = user.getCreatedAt();
        user.setCreatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }
}
