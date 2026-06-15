package com.alexeisoki.vibeboot.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import com.alexeisoki.vibeboot.user.User;
import com.alexeisoki.vibeboot.user.UserService;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "vibeboot.github.client-id=test-client-id",
                "vibeboot.github.redirect-uri=http://localhost:8080/auth/github/callback",
                "vibeboot.github.authorization-uri=https://github.test/login/oauth/authorize",
                "vibeboot.github.scope=read:user,user:email"
        }
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubOAuthService gitHubOAuthService;

    @MockitoBean
    private UserService userService;

    @Test
    void login_redirectsToGitHubAuthorizationUrl() throws Exception {
        mockMvc.perform(get("/auth/github/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://github.test/login/oauth/authorize")))
                .andExpect(header().string("Location", containsString("client_id=test-client-id")))
                .andExpect(header().string("Location", containsString("redirect_uri=http://localhost:8080/auth/github/callback")))
                .andExpect(header().string("Location", containsString("response_type=code")))
                .andExpect(header().string("Location", containsString("scope=read:user,user:email")));
    }

    @Test
    void callback_exchangesCodeStoresUserIdInSessionAndRedirects() throws Exception {
        UUID userId = UUID.randomUUID();
        GitHubUserProfile profile = new GitHubUserProfile(
                12345L,
                "alexei",
                "Alexei",
                "alexei@example.com",
                "https://avatars.githubusercontent.com/u/12345"
        );
        User user = userWithId(userId);

        when(gitHubOAuthService.exchangeCodeForToken("temporary-code")).thenReturn("github-access-token");
        when(gitHubOAuthService.fetchGitHubUser("github-access-token")).thenReturn(profile);
        when(userService.findOrCreateFromGitHubProfile(profile)).thenReturn(user);

        mockMvc.perform(get("/auth/github/callback").param("code", "temporary-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andExpect(request().sessionAttribute(AuthController.USER_ID_SESSION_ATTRIBUTE, userId));

        verify(gitHubOAuthService).exchangeCodeForToken("temporary-code");
        verify(gitHubOAuthService).fetchGitHubUser("github-access-token");
        verify(userService).findOrCreateFromGitHubProfile(profile);
    }

    @Test
    void callback_returnsBadRequestWhenCodeIsMissing() throws Exception {
        mockMvc.perform(get("/auth/github/callback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_returnsUnauthorizedWhenNoUserIsStoredInSession() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsCurrentUserWhenUserIdIsStoredInSession() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.USER_ID_SESSION_ATTRIBUTE, userId);
        User user = userWithId(userId);

        when(userService.getUserOrThrow(userId)).thenReturn(user);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.githubId").value(12345))
                .andExpect(jsonPath("$.githubUsername").value("alexei"))
                .andExpect(jsonPath("$.name").value("Alexei"))
                .andExpect(jsonPath("$.email").value("alexei@example.com"))
                .andExpect(jsonPath("$.avatarUrl").value("https://avatars.githubusercontent.com/u/12345"));
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private static User userWithId(UUID userId) {
        User user = new User(
                12345L,
                "alexei",
                "Alexei",
                "alexei@example.com",
                "https://avatars.githubusercontent.com/u/12345"
        );
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
