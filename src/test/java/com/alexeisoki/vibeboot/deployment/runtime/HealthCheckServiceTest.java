package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class HealthCheckServiceTest {

    @Test
    void waitUntilHealthy_returnsHealthyWhenFirstProbeSucceeds() {
        List<URI> probedUris = new ArrayList<>();
        HealthCheckService healthCheckService = new HealthCheckService(
                healthUri -> {
                    probedUris.add(healthUri);
                    return true;
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(1),
                ignored -> {
                    throw new AssertionError("Should not sleep after a successful first probe");
                }
        );

        HealthCheckResult result = healthCheckService.waitUntilHealthy(
                "http://localhost:49152",
                "/health"
        );

        assertThat(result.healthy()).isTrue();
        assertThat(result.healthUri()).isEqualTo(URI.create("http://localhost:49152/health"));
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Health check succeeded");
        assertThat(probedUris).containsExactly(URI.create("http://localhost:49152/health"));
    }

    @Test
    void waitUntilHealthy_pollsUntilProbeSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> sleeps = new ArrayList<>();
        HealthCheckService healthCheckService = new HealthCheckService(
                ignored -> attempts.incrementAndGet() == 3,
                Duration.ofSeconds(1),
                Duration.ofMillis(1),
                sleeps::add
        );

        HealthCheckResult result = healthCheckService.waitUntilHealthy(
                "http://localhost:49152/",
                "health"
        );

        assertThat(result.healthy()).isTrue();
        assertThat(result.healthUri()).isEqualTo(URI.create("http://localhost:49152/health"));
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void waitUntilHealthy_returnsUnhealthyWhenTimeoutExpires() {
        HealthCheckService healthCheckService = new HealthCheckService(
                ignored -> false,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                ignored -> {
                }
        );

        HealthCheckResult result = healthCheckService.waitUntilHealthy(
                "http://localhost:49152",
                "/health"
        );

        assertThat(result.healthy()).isFalse();
        assertThat(result.healthUri()).isEqualTo(URI.create("http://localhost:49152/health"));
        assertThat(result.attempts()).isGreaterThanOrEqualTo(1);
        assertThat(result.message()).isEqualTo("Health check timed out");
    }

    @Test
    void waitUntilHealthy_rejectsBlankDeploymentUrl() {
        HealthCheckService healthCheckService = healthCheckService();

        assertThatThrownBy(() -> healthCheckService.waitUntilHealthy("", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("deploymentUrl must not be blank");
    }

    @Test
    void constructor_rejectsInvalidTimeout() {
        assertThatThrownBy(() -> new HealthCheckService(
                ignored -> true,
                Duration.ZERO,
                Duration.ofMillis(1),
                ignored -> {
                }
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive");
    }

    private HealthCheckService healthCheckService() {
        return new HealthCheckService(
                ignored -> true,
                Duration.ofSeconds(1),
                Duration.ofMillis(1),
                ignored -> {
                }
        );
    }
}
