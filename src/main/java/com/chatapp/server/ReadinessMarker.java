package com.chatapp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

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
        heartbeat();
    }

    void heartbeat() throws IOException {
        BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) throw new IOException("Readiness marker attribute view is unavailable.");
        BasicFileAttributes attributes = view.readAttributes();
        if (!attributes.isRegularFile()) throw new IOException("Readiness marker must be a regular file.");
        view.setTimes(FileTime.fromMillis(System.currentTimeMillis()), null, null);
    }

    void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; the healthcheck will fail once the process exits.
        }
    }
}
