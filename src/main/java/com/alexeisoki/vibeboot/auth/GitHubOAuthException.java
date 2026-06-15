package com.alexeisoki.vibeboot.auth;

public class GitHubOAuthException extends RuntimeException {
    GitHubOAuthException(String message) {
        super(message);
    }

    GitHubOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
