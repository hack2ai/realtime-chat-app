package com.chatapp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manages the process-local readiness/heartbeat marker used by container health checks. */
final class ReadinessMarker {
    private static final long HEARTBEAT_INTERVAL_MILLIS = 60_000L;

    private final Path path;
    private final AtomicBoolean heartbeatRunning = new AtomicBoolean();
    private volatile Thread heartbeatThread;

    ReadinessMarker(Path path) {
        if (path == null) throw new IllegalArgumentException("Readiness marker path must not be null.");
        this.path = path;
    }

    synchronized void markReady() throws IOException {
        stopHeartbeat();
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.deleteIfExists(path);
        Files.createFile(path);
        heartbeatRunning.set(true);
        heartbeatThread = Thread.startVirtualThread(this::heartbeatLoop);
    }

    void refresh() throws IOException {
        if (!Files.exists(path)) return;
        Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
    }

    private void heartbeatLoop() {
        while (heartbeatRunning.get()) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MILLIS);
                if (heartbeatRunning.get()) refresh();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                return;
            }
        }
    }

    synchronized void clear() {
        stopHeartbeat();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; the healthcheck will fail once the process exits.
        }
    }

    private synchronized void stopHeartbeat() {
        heartbeatRunning.set(false);
        Thread thread = heartbeatThread;
        heartbeatThread = null;
        if (thread != null) thread.interrupt();
    }
}
