package com.alexeisoki.vibeboot.deployment.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
    private static final String WORKSPACE_DIRECTORY_NAME = "vibeboot-workspaces";
    private static final String DEPLOYMENT_WORKSPACE_PREFIX = "deployment-";

    private final Path workspaceRoot;

    public WorkspaceService() {
        this.workspaceRoot = Path.of(
                System.getProperty("java.io.tmpdir"),
                WORKSPACE_DIRECTORY_NAME
        ).toAbsolutePath().normalize();
    }

    public Path createWorkspace(UUID deploymentId) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }

        try {
            Files.createDirectories(workspaceRoot);
            return Files.createTempDirectory(
                    workspaceRoot,
                    DEPLOYMENT_WORKSPACE_PREFIX + deploymentId + "-"
            );
        } catch (IOException exception) {
            throw new WorkspaceServiceException("Could not create deployment workspace", exception);
        }
    }

    public void cleanupWorkspace(Path workspace) {
        if (workspace == null) {
            return;
        }

        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        if (!normalizedWorkspace.startsWith(workspaceRoot) || normalizedWorkspace.equals(workspaceRoot)) {
            throw new IllegalArgumentException("workspace must be inside the Vibe Boot workspace root");
        }

        if (Files.notExists(normalizedWorkspace)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(normalizedWorkspace)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(this::delete);
        } catch (IOException | UncheckedIOException exception) {
            throw new WorkspaceServiceException("Could not clean up deployment workspace", exception);
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
