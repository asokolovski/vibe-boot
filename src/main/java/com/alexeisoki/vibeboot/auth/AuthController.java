package com.alexeisoki.vibeboot.auth;

import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.alexeisoki.vibeboot.auth.dto.CurrentUserResponse;
import com.alexeisoki.vibeboot.user.User;
import com.alexeisoki.vibeboot.user.UserService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AuthController {
    public static final String USER_ID_SESSION_ATTRIBUTE = "USER_ID";

    private final GitHubOAuthService gitHubOAuthService;
    private final UserService userService;
    private final String clientId;
    private final String redirectUri;
    private final String authorizationUri;
    private final String scope;

    public AuthController(
            GitHubOAuthService gitHubOAuthService,
            UserService userService,
            @Value("${vibeboot.github.client-id}") String clientId,
            @Value("${vibeboot.github.redirect-uri:http://localhost:8080/auth/github/callback}") String redirectUri,
            @Value("${vibeboot.github.authorization-uri:https://github.com/login/oauth/authorize}") String authorizationUri,
            @Value("${vibeboot.github.scope:read:user,user:email}") String scope
    ) {
        this.gitHubOAuthService = gitHubOAuthService;
        this.userService = userService;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.authorizationUri = authorizationUri;
        this.scope = scope;
    }

    @GetMapping("/auth/github/login")
    public ResponseEntity<Void> login() {
        URI githubAuthorizeUri = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .build()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(githubAuthorizeUri)
                .build();
    }

    @GetMapping("/auth/github/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, HttpSession session) {
        String accessToken = gitHubOAuthService.exchangeCodeForToken(code);
        GitHubUserProfile profile = gitHubOAuthService.fetchGitHubUser(accessToken);
        User user = userService.findOrCreateFromGitHubProfile(profile);

        session.setAttribute(USER_ID_SESSION_ATTRIBUTE, user.getId());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/"))
                .build();
    }

    @GetMapping("/api/me")
    public ResponseEntity<CurrentUserResponse> me(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_SESSION_ATTRIBUTE);
        if (!(userId instanceof UUID currentUserId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.getUserOrThrow(currentUserId);
        return ResponseEntity.ok(CurrentUserResponse.from(user));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
