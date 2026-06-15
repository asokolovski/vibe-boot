package com.alexeisoki.vibeboot.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GitHubOAuthService {
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String userUri;
    private final RestClient restClient;

    @Autowired
    public GitHubOAuthService(
            @Value("${vibeboot.github.client-id}") String clientId,
            @Value("${vibeboot.github.client-secret}") String clientSecret,
            @Value("${vibeboot.github.redirect-uri:http://localhost:8080/auth/github/callback}") String redirectUri,
            @Value("${vibeboot.github.token-uri:https://github.com/login/oauth/access_token}") String tokenUri,
            @Value("${vibeboot.github.user-uri:https://api.github.com/user}") String userUri
    ) {
        this(clientId, clientSecret, redirectUri, tokenUri, userUri, RestClient.create());
    }

    GitHubOAuthService(
            String clientId,
            String clientSecret,
            String redirectUri,
            String tokenUri,
            String userUri,
            RestClient restClient
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUri = tokenUri;
        this.userUri = userUri;
        this.restClient = restClient;
    }

    public String exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        try {
            GitHubAccessTokenResponse response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(GitHubAccessTokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new GitHubOAuthException("GitHub token response did not include an access token");
            }

            return response.accessToken();
        } catch (RestClientException exception) {
            throw new GitHubOAuthException("Could not exchange GitHub OAuth code for access token", exception);
        }
    }

    public GitHubUserProfile fetchGitHubUser(String accessToken) {
        try {
            GitHubUserProfile profile = restClient.get()
                    .uri(userUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GitHubUserProfile.class);

            if (profile == null || profile.githubId() == null || profile.githubUsername() == null
                    || profile.githubUsername().isBlank()) {
                throw new GitHubOAuthException("GitHub user response did not include required identity fields");
            }

            return profile;
        } catch (RestClientException exception) {
            throw new GitHubOAuthException("Could not fetch GitHub user profile", exception);
        }
    }
}
