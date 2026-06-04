package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkspaceServiceTest {

    private final WorkspaceService workspaceService = new WorkspaceService();

    @Test
    void createWorkspace_createsUniqueDeploymentWorkspace() {
        UUID deploymentId = UUID.randomUUID();
        Path firstWorkspace = workspaceService.createWorkspace(deploymentId);
        Path secondWorkspace = workspaceService.createWorkspace(deploymentId);

        try {
            assertThat(firstWorkspace).exists().isDirectory();
            assertThat(secondWorkspace).exists().isDirectory();
            assertThat(firstWorkspace).isNotEqualTo(secondWorkspace);
            assertThat(firstWorkspace.getFileName().toString())
                    .startsWith("deployment-" + deploymentId + "-");
        } finally {
            workspaceService.cleanupWorkspace(firstWorkspace);
            workspaceService.cleanupWorkspace(secondWorkspace);
        }
    }

    @Test
    void cleanupWorkspace_deletesWorkspaceRecursively() throws IOException {
        Path workspace = workspaceService.createWorkspace(UUID.randomUUID());
        Path sourceDirectory = Files.createDirectories(workspace.resolve("source/nested"));
        Path sourceFile = Files.writeString(sourceDirectory.resolve("app.txt"), "hello");

        workspaceService.cleanupWorkspace(workspace);

        assertThat(sourceFile).doesNotExist();
        assertThat(sourceDirectory).doesNotExist();
        assertThat(workspace).doesNotExist();
    }

    @Test
    void cleanupWorkspace_allowsNullOrAlreadyDeletedWorkspace() {
        Path workspace = workspaceService.createWorkspace(UUID.randomUUID());
        workspaceService.cleanupWorkspace(workspace);

        workspaceService.cleanupWorkspace(null);
        workspaceService.cleanupWorkspace(workspace);

        assertThat(workspace).doesNotExist();
    }

    @Test
    void cleanupWorkspace_rejectsPathOutsideWorkspaceRoot() {
        Path outsidePath = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        assertThatThrownBy(() -> workspaceService.cleanupWorkspace(outsidePath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workspace must be inside the Vibe Boot workspace root");
    }

    @Test
    void createWorkspace_rejectsMissingDeploymentId() {
        assertThatThrownBy(() -> workspaceService.createWorkspace(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("deploymentId must not be null");
    }
}
