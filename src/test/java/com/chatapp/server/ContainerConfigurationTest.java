package com.chatapp.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerConfigurationTest {
    private static final Path DOCKERFILE = Path.of("Dockerfile");

    @Test
    void dockerfileUsesExplicitBaseImageTags() throws IOException {
        List<String> fromLines = Files.readAllLines(DOCKERFILE).stream()
                .filter(line -> line.startsWith("FROM "))
                .toList();

        assertFalse(fromLines.isEmpty(), "Dockerfile must declare at least one base image.");
        assertTrue(fromLines.stream().allMatch(ContainerConfigurationTest::hasExplicitTag),
                "Docker base images must use explicit tags.");
        assertTrue(fromLines.stream().noneMatch(line -> line.endsWith(":latest") || line.contains(":latest ")),
                "Docker base images must not use the mutable latest tag.");
    }

    @Test
    void dockerfileEnforcesNonRootRuntime() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE);

        assertTrue(dockerfile.contains("USER chatapp"),
                "The server container must run as the dedicated non-root user.");
        assertTrue(dockerfile.contains("--uid 10001"),
                "The dedicated container user must retain the expected UID.");
        assertTrue(dockerfile.contains("STOPSIGNAL SIGTERM"),
                "The server container must use SIGTERM for graceful shutdown.");
    }

    @Test
    void dockerfileDeclaresServerHealthContract() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE);

        assertTrue(dockerfile.contains("EXPOSE 5050"),
                "The server container must expose the application port.");
        assertTrue(dockerfile.contains("VOLUME [\"/app/data/attachments\"]"),
                "Attachment data must have a dedicated container volume.");
        assertTrue(dockerfile.contains("HEALTHCHECK"),
                "The server container must declare a health check.");
        assertTrue(dockerfile.contains("com.chatapp.server.ServerHealthCheck"),
                "The container health check must use the application protocol probe.");
        assertTrue(dockerfile.contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/chatapp-server.jar\"]"),
                "The container must start the runnable server JAR directly.");
    }

    private static boolean hasExplicitTag(String fromLine) {
        String imageReference = fromLine.substring("FROM ".length()).trim().split("\\s+")[0];
        return imageReference.contains(":") && !imageReference.endsWith(":");
    }
}
