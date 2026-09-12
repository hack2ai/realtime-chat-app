package com.chatapp.server;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Manages the process-local readiness marker used by container health checks. */
final class ReadinessMarker {
    private static final Path PROC_SELF = Path.of("/proc/self");

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
            Files.deleteIfExists(temp);
            if (!createProcessBoundMarker(temp)) {
                Files.createFile(temp);
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private boolean createProcessBoundMarker(Path temp) {
        if (!Files.exists(PROC_SELF)) return false;
        long pid = ProcessHandle.current().pid();
        Path processStatus = Path.of("/proc", Long.toString(pid), "status");
        try {
            Files.createSymbolicLink(temp, processStatus);
            return true;
        } catch (UnsupportedOperationException | SecurityException | IOException e) {
            return false;
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
