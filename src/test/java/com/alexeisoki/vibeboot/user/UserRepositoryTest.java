package com.alexeisoki.vibeboot.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_persistsUserAndSetsGeneratedFields() {
        User savedUser = userRepository.save(new User(
                12345L,
                "alexei",
                "Alexei",
                "alexei@example.com",
                "https://avatars.githubusercontent.com/u/12345"
        ));

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getGithubId()).isEqualTo(12345L);
        assertThat(savedUser.getGithubUsername()).isEqualTo("alexei");
        assertThat(savedUser.getCreatedAt()).isNotNull();
    }

    @Test
    void findByGithubId_returnsMatchingUser() {
        User savedUser = userRepository.save(new User(
                12345L,
                "alexei",
                null,
                null,
                null
        ));

        assertThat(userRepository.findByGithubId(12345L)).contains(savedUser);
        assertThat(userRepository.findByGithubId(99999L)).isEmpty();
    }
}
