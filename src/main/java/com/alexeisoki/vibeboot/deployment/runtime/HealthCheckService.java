package com.alexeisoki.vibeboot.deployment.runtime;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class HealthCheckService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    // These two fields are replaceable versions of the core behavior:
    // isHealthy(healthUri) and sleep(pollInterval).
    // Production uses real HTTP/sleep; tests swap in instant fake functions.
    private final Predicate<URI> healthProbe;
    private final Duration timeout;
    private final Duration pollInterval;
    private final Consumer<Duration> sleeper;

    public HealthCheckService() {
        this(
                new RestClientHealthProbe(RestClient.create()),
                DEFAULT_TIMEOUT,
                DEFAULT_POLL_INTERVAL,
                HealthCheckService::sleep
        );
    }

    HealthCheckService(
            Predicate<URI> healthProbe,
            Duration timeout,
            Duration pollInterval,
            Consumer<Duration> sleeper
    ) {
        validateTimeout(timeout);
        validatePollInterval(pollInterval);
        if (healthProbe == null) {
            throw new IllegalArgumentException("healthProbe must not be null");
        }
        if (sleeper == null) {
            throw new IllegalArgumentException("sleeper must not be null");
        }

        this.healthProbe = healthProbe;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
        this.sleeper = sleeper;
    }

    public HealthCheckResult waitUntilHealthy(String deploymentUrl, String healthCheckPath) {
        URI healthUri = toHealthUri(deploymentUrl, healthCheckPath);
        long deadline = System.nanoTime() + timeout.toNanos();
        int attempts = 0;

        while (true) {
            attempts++;
            if (healthProbe.test(healthUri)) {
                return HealthCheckResult.healthy(healthUri, attempts);
            }

            if (System.nanoTime() >= deadline) {
                return HealthCheckResult.unhealthy(healthUri, attempts);
            }

            // Equivalent to sleep(pollInterval), but injectable so tests do not wait.
            sleeper.accept(pollInterval);
        }
    }

    private URI toHealthUri(String deploymentUrl, String healthCheckPath) {
        validateText(deploymentUrl, "deploymentUrl");
        validateText(healthCheckPath, "healthCheckPath");

        String baseUrl = deploymentUrl.endsWith("/")
                ? deploymentUrl.substring(0, deploymentUrl.length() - 1)
                : deploymentUrl;
        String path = healthCheckPath.startsWith("/")
                ? healthCheckPath
                : "/" + healthCheckPath;

        return URI.create(baseUrl + path);
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    private static void validatePollInterval(Duration pollInterval) {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Health check was interrupted", exception);
        }
    }

    private record RestClientHealthProbe(RestClient restClient) implements Predicate<URI> {

        @Override
        public boolean test(URI healthUri) {
            try {
                return restClient.get()
                        .uri(healthUri)
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                        .is2xxSuccessful();
            } catch (RestClientException exception) {
                return false;
            }
        }
    }
}

/*
 * Learning reference: this is the simpler version we would have written without
 * injectable test doubles. It is easier to read, but harder to test because it
 * always makes real HTTP calls and always sleeps for real time.
 *
 * @Service
 * public class HealthCheckService {
 *     private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
 *     private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
 *
 *     private final RestClient restClient = RestClient.create();
 *
 *     public HealthCheckResult waitUntilHealthy(String deploymentUrl, String healthCheckPath) {
 *         URI healthUri = toHealthUri(deploymentUrl, healthCheckPath);
 *         long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
 *         int attempts = 0;
 *
 *         while (true) {
 *             attempts++;
 *
 *             if (isHealthy(healthUri)) {
 *                 return HealthCheckResult.healthy(healthUri, attempts);
 *             }
 *
 *             if (System.nanoTime() >= deadline) {
 *                 return HealthCheckResult.unhealthy(healthUri, attempts);
 *             }
 *
 *             sleep(DEFAULT_POLL_INTERVAL);
 *         }
 *     }
 *
 *     private boolean isHealthy(URI healthUri) {
 *         try {
 *             return restClient.get()
 *                     .uri(healthUri)
 *                     .retrieve()
 *                     .toBodilessEntity()
 *                     .getStatusCode()
 *                     .is2xxSuccessful();
 *         } catch (RestClientException exception) {
 *             return false;
 *         }
 *     }
 *
 *     private static void sleep(Duration duration) {
 *         try {
 *             Thread.sleep(duration.toMillis());
 *         } catch (InterruptedException exception) {
 *             Thread.currentThread().interrupt();
 *             throw new IllegalStateException("Health check was interrupted", exception);
 *         }
 *     }
 * }
 */
