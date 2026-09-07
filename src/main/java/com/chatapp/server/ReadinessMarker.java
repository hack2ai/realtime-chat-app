package com.chatapp.server;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

        Path temp = Files.createTempFile(
            parent == null ? Path.of(".") : parent,
            ".chatapp-ready-",
            ".tmp"
        );
        try {
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; the healthcheck will fail once the process exits.
        }
    }
}
