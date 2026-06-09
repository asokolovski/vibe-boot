package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRunnerTest {

    private final CommandRunner commandRunner = new CommandRunner();

    @Test
    void run_capturesStdoutStderrAndExitCode() {
        CommandResult result = commandRunner.run(
                List.of("sh", "-c", "printf 'hello'; printf 'problem' >&2; exit 7"),
                Duration.ofSeconds(1)
        );

        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.stdout()).isEqualTo("hello");
        assertThat(result.stderr()).isEqualTo("problem");
        assertThat(result.timedOut()).isFalse();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void run_marksSuccessfulZeroExitCommandAsSucceeded() {
        CommandResult result = commandRunner.run(
                List.of("sh", "-c", "printf 'ok'"),
                Duration.ofSeconds(1)
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("ok");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void run_usesWorkingDirectoryWhenProvided(@TempDir Path tempDirectory) {
        CommandResult result = commandRunner.run(
                List.of("sh", "-c", "pwd"),
                tempDirectory,
                Duration.ofSeconds(1)
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).isEqualTo(tempDirectory.toString());
    }

    @Test
    void run_appliesEnvironmentOverrides() {
        CommandResult result = commandRunner.run(
                List.of("sh", "-c", "printf '%s' \"$VIBEBOOT_COMMAND_RUNNER_TEST\""),
                Map.of("VIBEBOOT_COMMAND_RUNNER_TEST", "env-value"),
                Duration.ofSeconds(1)
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("env-value");
    }

    @Test
    void run_timesOutLongRunningCommand() {
        CommandResult result = commandRunner.run(
                List.of("sh", "-c", "sleep 2"),
                Duration.ofMillis(100)
        );

        assertThat(result.timedOut()).isTrue();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void run_returnsStructuredFailureWhenCommandCannotStart() {
        CommandResult result = commandRunner.run(
                List.of("definitely-not-a-real-command-vibeboot"),
                Duration.ofSeconds(1)
        );

        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).contains("definitely-not-a-real-command-vibeboot");
        assertThat(result.timedOut()).isFalse();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void run_rejectsEmptyCommand() {
        assertThatThrownBy(() -> commandRunner.run(List.of(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("command must not be empty");
    }

    @Test
    void run_rejectsMissingTimeout() {
        assertThatThrownBy(() -> commandRunner.run(List.of("sh", "-c", "printf ok"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive");
    }
}
