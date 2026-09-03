package com.chatapp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Manages the process-local readiness marker used by container health checks. */
final class ReadinessMarker {
    private final Path path;

    ReadinessMarker(Path path) {
        if (path == null) throw new IllegalArgumentException("Readiness marker path must not be null.");
        this.path = path;
    }

    void markReady() throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.deleteIfExists(path);
        Files.createFile(path);
    }

    void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; the healthcheck will fail once the process exits.
        }
    }
}
