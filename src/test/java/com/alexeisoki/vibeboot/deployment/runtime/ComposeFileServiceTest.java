package com.alexeisoki.vibeboot.deployment.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alexeisoki.vibeboot.project.Project;
import com.alexeisoki.vibeboot.project.ProjectSourceType;

class ComposeFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createVibeBootComposeFile_removesAllPublishedPortsAndPublishesOnlyPrimaryService() throws Exception {
        Path sourceDirectory = tempDir.resolve("source");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("compose.yaml"), """
                services:
                  frontend:
                    build:
                      context: ./frontend
                    depends_on:
                      - api
                    ports:
                      - "8080:80"
                  api:
                    build:
                      context: .
                      target: runtime
                    ports:
                      - "3000:3000"
                  database:
                    image: postgres:17-alpine
                    ports:
                      - "${POSTGRES_PORT}:5432"
                volumes:
                  postgres_data:
                """);
        Project project = composeProject(null);
        ComposeFileService composeFileService = new ComposeFileService();

        ComposeFileResult result = composeFileService.createVibeBootComposeFile(project, sourceDirectory, 49152);

        assertThat(result.containerPort()).isEqualTo(80);
        assertThat(result.composeFile()).isEqualTo(sourceDirectory.resolve("vibeboot.compose.yaml"));

        String generatedCompose = Files.readString(result.composeFile());
        assertThat(generatedCompose).contains("frontend:");
        assertThat(generatedCompose).contains("- \"49152:80\"");
        assertThat(generatedCompose).contains("api:");
        assertThat(generatedCompose).contains("database:");
        assertThat(generatedCompose).doesNotContain("8080:80");
        assertThat(generatedCompose).doesNotContain("3000:3000");
        assertThat(generatedCompose).doesNotContain("${POSTGRES_PORT}:5432");
    }

    @Test
    void createVibeBootComposeFile_usesExplicitContainerPortWhenPrimaryServiceHasNoPorts() throws Exception {
        Path sourceDirectory = tempDir.resolve("source");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("compose.yaml"), """
                services:
                  frontend:
                    build:
                      context: ./frontend
                  api:
                    build:
                      context: .
                """);
        Project project = composeProject(5173);
        ComposeFileService composeFileService = new ComposeFileService();

        ComposeFileResult result = composeFileService.createVibeBootComposeFile(project, sourceDirectory, 49152);

        assertThat(result.containerPort()).isEqualTo(5173);
        assertThat(Files.readString(result.composeFile())).contains("- \"49152:5173\"");
    }

    @Test
    void createVibeBootComposeFile_throwsWhenContainerPortCannotBeInferred() throws Exception {
        Path sourceDirectory = tempDir.resolve("source");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("compose.yaml"), """
                services:
                  frontend:
                    build:
                      context: ./frontend
                """);
        Project project = composeProject(null);
        ComposeFileService composeFileService = new ComposeFileService();

        assertThatThrownBy(() -> composeFileService.createVibeBootComposeFile(project, sourceDirectory, 49152))
                .isInstanceOf(DockerServiceException.class)
                .hasMessageContaining("Could not infer container port");
    }

    private Project composeProject(Integer containerPort) {
        return new Project(
                "YT Clipper",
                "https://github.com/asokolovski/yt-clipper-mvp",
                "main",
                null,
                containerPort,
                "/",
                null,
                ProjectSourceType.DOCKER_COMPOSE,
                null,
                "compose.yaml",
                "frontend"
        );
    }
}
