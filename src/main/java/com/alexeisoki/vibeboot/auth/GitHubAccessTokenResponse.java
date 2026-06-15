package com.alexeisoki.vibeboot.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

record GitHubAccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope
) {
}
