package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitServiceTest {
    private static final String REPOSITORY_URL = "https://github.com/alexeisoko/example-app";

    @Mock
    private CommandRunner commandRunner;

    @Test
    void cloneRepository_runsShallowSingleBranchCloneAndReturnsOutput(@TempDir Path targetDirectory) {
        GitService gitService = new GitService(commandRunner);
        List<String> command = cloneCommand("main", targetDirectory);

        when(commandRunner.run(command, nonInteractiveGitEnvironment(), Duration.ofMinutes(2)))
                .thenReturn(new CommandResult(
                        0,
                        "",
                        "Cloning into '" + targetDirectory.getFileName() + "'...\n",
                        false
                ));

        GitCloneResult result = gitService.cloneRepository(REPOSITORY_URL, "main", targetDirectory);

        assertThat(result.output()).contains("Cloning into");
        verify(commandRunner).run(command, nonInteractiveGitEnvironment(), Duration.ofMinutes(2));
    }

    @Test
    void cloneRepository_usesConfiguredBranch(@TempDir Path targetDirectory) {
        GitService gitService = new GitService(commandRunner);
        List<String> command = cloneCommand("develop", targetDirectory);

        when(commandRunner.run(command, nonInteractiveGitEnvironment(), Duration.ofMinutes(2)))
                .thenReturn(new CommandResult(0, "", "", false));

        gitService.cloneRepository(REPOSITORY_URL, "develop", targetDirectory);

        verify(commandRunner).run(command, nonInteractiveGitEnvironment(), Duration.ofMinutes(2));
    }

    @Test
    void cloneRepository_throwsUsefulExceptionWhenCloneFails(@TempDir Path targetDirectory) {
        GitService gitService = new GitService(commandRunner);
        List<String> command = cloneCommand("missing-branch", targetDirectory);

        when(commandRunner.run(command, nonInteractiveGitEnvironment(), Duration.ofMinutes(2)))
                .thenReturn(new CommandResult(
                        128,
                        "",
                        "fatal: Remote branch missing-branch not found",
                        false
                ));

        assertThatThrownBy(() -> gitService.cloneRepository(REPOSITORY_URL, "missing-branch", targetDirectory))
                .isInstanceOfSatisfying(GitServiceException.class, exception -> {
                    assertThat(exception)
                            .hasMessageContaining("Git repository clone failed")
                            .hasMessageContaining("Remote branch missing-branch not found");
                    assertThat(exception.commandOutput())
                            .contains("fatal: Remote branch missing-branch not found");
                });
    }

    @Test
    void cloneRepository_reportsTimeout(@TempDir Path targetDirectory) {
        GitService gitService = new GitService(commandRunner);
        List<String> command = cloneCommand("main", targetDirectory);

        when(commandRunner.run(command, nonInteractiveGitEnvironment(), Duration.ofMinutes(2)))
                .thenReturn(new CommandResult(-1, "", "", true));

        assertThatThrownBy(() -> gitService.cloneRepository(REPOSITORY_URL, "main", targetDirectory))
                .isInstanceOf(GitServiceException.class)
                .hasMessage("Git repository clone failed: command timed out");
    }

    @Test
    void cloneRepository_rejectsNonGithubHttpsRepository(@TempDir Path targetDirectory) {
        GitService gitService = new GitService(commandRunner);

        assertThatThrownBy(() -> gitService.cloneRepository(
                "git@github.com:alexeisoko/example-app.git",
                "main",
                targetDirectory
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repositoryUrl must be a public HTTPS GitHub repository URL");

        verify(commandRunner, never()).run(
                anyList(),
                anyMap(),
                any(Duration.class)
        );
    }

    @Test
    void cloneRepository_rejectsBlankBranch(@TempDir Path targetDirectory) {
        GitService gitService = new GitService(commandRunner);

        assertThatThrownBy(() -> gitService.cloneRepository(REPOSITORY_URL, " ", targetDirectory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("branch must not be blank");

        verify(commandRunner, never()).run(
                anyList(),
                anyMap(),
                any(Duration.class)
        );
    }

    @Test
    void cloneRepository_rejectsMissingTargetDirectory() {
        GitService gitService = new GitService(commandRunner);

        assertThatThrownBy(() -> gitService.cloneRepository(REPOSITORY_URL, "main", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetDirectory must not be null");

        verify(commandRunner, never()).run(
                anyList(),
                anyMap(),
                any(Duration.class)
        );
    }

    private List<String> cloneCommand(String branch, Path targetDirectory) {
        return List.of(
                "git",
                "clone",
                "--depth",
                "1",
                "--branch",
                branch,
                "--single-branch",
                REPOSITORY_URL,
                targetDirectory.toAbsolutePath().normalize().toString()
        );
    }

    private Map<String, String> nonInteractiveGitEnvironment() {
        return Map.of(
                "GIT_TERMINAL_PROMPT", "0",
                "GCM_INTERACTIVE", "never",
                "GIT_ASKPASS", "",
                "SSH_ASKPASS", ""
        );
    }
}
