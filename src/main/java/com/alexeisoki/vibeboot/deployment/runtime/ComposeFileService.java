package com.alexeisoki.vibeboot.deployment.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alexeisoki.vibeboot.project.Project;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Service
public class ComposeFileService {
    private static final String GENERATED_COMPOSE_FILE_NAME = "vibeboot.compose.yaml";

    private final ObjectMapper yamlMapper;

    public ComposeFileService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public ComposeFileResult createVibeBootComposeFile(
            Project project,
            Path sourceDirectory,
            int hostPort
    ) {
        validateProject(project);
        validateSourceDirectory(sourceDirectory);
        validateHostPort(hostPort);

        Path originalComposeFile = resolveInsideSourceDirectory(sourceDirectory, project.getComposeFilePath());
        Map<String, Object> composeDocument = readComposeDocument(originalComposeFile);
        Map<String, Object> services = services(composeDocument);
        Map<String, Object> primaryService = service(services, project.getPrimaryServiceName());

        int containerPort = project.getContainerPort() != null
                ? project.getContainerPort()
                : inferContainerPort(primaryService, project.getPrimaryServiceName());

        removePublishedPorts(services);
        primaryService.put("ports", List.of(hostPort + ":" + containerPort));

        Path generatedComposeFile = originalComposeFile.getParent().resolve(GENERATED_COMPOSE_FILE_NAME);
        writeComposeDocument(generatedComposeFile, composeDocument);

        return new ComposeFileResult(generatedComposeFile, containerPort);
    }

    private Path resolveInsideSourceDirectory(Path sourceDirectory, String composeFilePath) {
        Path resolvedPath = sourceDirectory.resolve(composeFilePath).normalize();
        if (!resolvedPath.startsWith(sourceDirectory.normalize())) {
            throw new IllegalArgumentException("composeFilePath must stay inside the repository");
        }

        return resolvedPath;
    }

    private Map<String, Object> readComposeDocument(Path composeFile) {
        if (!Files.isRegularFile(composeFile)) {
            throw new DockerServiceException("Docker Compose file not found: " + composeFile);
        }

        try {
            Map<String, Object> composeDocument = yamlMapper.readValue(
                    composeFile.toFile(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );

            if (composeDocument == null || composeDocument.isEmpty()) {
                throw new DockerServiceException("Docker Compose file is empty: " + composeFile);
            }

            return composeDocument;
        } catch (IOException exception) {
            throw new DockerServiceException("Could not read Docker Compose file: " + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> services(Map<String, Object> composeDocument) {
        Object services = composeDocument.get("services");
        if (!(services instanceof Map<?, ?>)) {
            throw new DockerServiceException("Docker Compose file must define services");
        }

        return (Map<String, Object>) services;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> service(Map<String, Object> services, String serviceName) {
        Object service = services.get(serviceName);
        if (!(service instanceof Map<?, ?>)) {
            throw new DockerServiceException("Docker Compose service not found: " + serviceName);
        }

        return (Map<String, Object>) service;
    }

    @SuppressWarnings("unchecked")
    private int inferContainerPort(Map<String, Object> primaryService, String primaryServiceName) {
        Object ports = primaryService.get("ports");
        if (!(ports instanceof List<?> portMappings) || portMappings.isEmpty()) {
            throw new DockerServiceException(
                    "Could not infer container port for Compose service " + primaryServiceName
                            + "; set the project container port explicitly"
            );
        }

        for (Object portMapping : portMappings) {
            Integer inferredPort = inferContainerPortFromPortMapping(portMapping);
            if (inferredPort != null) {
                return inferredPort;
            }
        }

        throw new DockerServiceException(
                "Could not infer container port for Compose service " + primaryServiceName
                        + "; set the project container port explicitly"
        );
    }

    @SuppressWarnings("unchecked")
    private Integer inferContainerPortFromPortMapping(Object portMapping) {
        if (portMapping instanceof String stringPortMapping) {
            return inferContainerPortFromStringPortMapping(stringPortMapping);
        }

        if (portMapping instanceof Number numberPortMapping) {
            return numberPortMapping.intValue();
        }

        if (portMapping instanceof Map<?, ?> mapPortMapping) {
            Object target = ((Map<String, Object>) mapPortMapping).get("target");
            if (target instanceof Number targetPort) {
                return targetPort.intValue();
            }

            if (target instanceof String targetPort) {
                return parsePort(targetPort);
            }
        }

        return null;
    }

    private Integer inferContainerPortFromStringPortMapping(String portMapping) {
        String withoutProtocol = portMapping.split("/", 2)[0];
        String[] parts = withoutProtocol.split(":");
        String containerPort = parts[parts.length - 1];

        return parsePort(containerPort);
    }

    private Integer parsePort(String port) {
        try {
            int parsedPort = Integer.parseInt(port.trim());
            return parsedPort >= 1 && parsedPort <= 65535 ? parsedPort : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void removePublishedPorts(Map<String, Object> services) {
        for (Object service : services.values()) {
            if (service instanceof Map<?, ?> serviceMap) {
                ((Map<String, Object>) serviceMap).remove("ports");
            }
        }
    }

    private void writeComposeDocument(Path composeFile, Map<String, Object> composeDocument) {
        try {
            Files.writeString(composeFile, yamlMapper.writeValueAsString(composeDocument));
        } catch (IOException exception) {
            throw new DockerServiceException("Could not write generated Docker Compose file: " + exception.getMessage());
        }
    }

    private void validateProject(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }

        if (project.getComposeFilePath() == null || project.getComposeFilePath().isBlank()) {
            throw new IllegalArgumentException("composeFilePath must not be blank");
        }

        if (project.getPrimaryServiceName() == null || project.getPrimaryServiceName().isBlank()) {
            throw new IllegalArgumentException("primaryServiceName must not be blank");
        }
    }

    private void validateSourceDirectory(Path sourceDirectory) {
        if (sourceDirectory == null) {
            throw new IllegalArgumentException("sourceDirectory must not be null");
        }
    }

    private void validateHostPort(int hostPort) {
        if (hostPort <= 0) {
            throw new IllegalArgumentException("hostPort must be positive");
        }
    }
}
