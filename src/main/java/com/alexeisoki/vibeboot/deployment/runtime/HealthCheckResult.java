package com.alexeisoki.vibeboot.deployment.runtime;

import java.net.URI;

public record HealthCheckResult(
        boolean healthy,
        URI healthUri,
        int attempts,
        String message
) {
    static HealthCheckResult healthy(URI healthUri, int attempts) {
        return new HealthCheckResult(true, healthUri, attempts, "Health check succeeded");
    }

    static HealthCheckResult unhealthy(URI healthUri, int attempts) {
        return new HealthCheckResult(false, healthUri, attempts, "Health check timed out");
    }
}
