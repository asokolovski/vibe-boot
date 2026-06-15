package com.alexeisoki.vibeboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubOAuthServiceTest {
    private static final String TOKEN_URI = "https://github.test/login/oauth/access_token";
    private static final String USER_URI = "https://api.github.test/user";

    @Test
    void exchangeCodeForToken_postsCodeAndReturnsAccessToken() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GitHubOAuthService service = gitHubOAuthService(restClientBuilder.build());

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(POST))
                .andExpect(header(ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("client_id=test-client-id")))
                .andExpect(content().string(containsString("client_secret=test-client-secret")))
                .andExpect(content().string(containsString("code=temporary-code")))
                .andExpect(content().string(containsString("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fauth%2Fgithub%2Fcallback")))
                .andRespond(withSuccess(
                        """
                                {
                                  "access_token": "github-access-token",
                                  "token_type": "bearer",
                                  "scope": "read:user,user:email"
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        String accessToken = service.exchangeCodeForToken("temporary-code");

        assertThat(accessToken).isEqualTo("github-access-token");
        server.verify();
    }

    @Test
    void exchangeCodeForToken_throwsWhenGitHubTokenRequestFails() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GitHubOAuthService service = gitHubOAuthService(restClientBuilder.build());

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.exchangeCodeForToken("temporary-code"))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessage("Could not exchange GitHub OAuth code for access token");
        server.verify();
    }

    @Test
    void fetchGitHubUser_getsUserProfileWithBearerToken() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GitHubOAuthService service = gitHubOAuthService(restClientBuilder.build());

        server.expect(requestTo(USER_URI))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer github-access-token"))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": 12345,
                                  "login": "alexei",
                                  "name": "Alexei",
                                  "email": "alexei@example.com",
                                  "avatar_url": "https://avatars.githubusercontent.com/u/12345"
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        GitHubUserProfile profile = service.fetchGitHubUser("github-access-token");

        assertThat(profile.githubId()).isEqualTo(12345L);
        assertThat(profile.githubUsername()).isEqualTo("alexei");
        assertThat(profile.name()).isEqualTo("Alexei");
        assertThat(profile.email()).isEqualTo("alexei@example.com");
        assertThat(profile.avatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345");
        server.verify();
    }

    @Test
    void fetchGitHubUser_throwsWhenIdentityFieldsAreMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GitHubOAuthService service = gitHubOAuthService(restClientBuilder.build());

        server.expect(requestTo(USER_URI))
                .andExpect(method(GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.fetchGitHubUser("github-access-token"))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessage("GitHub user response did not include required identity fields");
        server.verify();
    }

    private static GitHubOAuthService gitHubOAuthService(RestClient restClient) {
        return new GitHubOAuthService(
                "test-client-id",
                "test-client-secret",
                "http://localhost:8080/auth/github/callback",
                TOKEN_URI,
                USER_URI,
                restClient
        );
    }
}
