package com.chatapp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** Small dependency-free health probe used by the container healthcheck. */
public final class ServerHealthCheck {
    static final long DEFAULT_MAX_AGE_MILLIS = 30_000L;

    private ServerHealthCheck() {
    }

    public static void main(String[] args) {
        Path markerPath = Path.of(System.getProperty("java.io.tmpdir"), "chatapp.ready");
        if (!isHealthy(markerPath, System.currentTimeMillis(), DEFAULT_MAX_AGE_MILLIS)) {
            System.exit(1);
        }
    }

    static boolean isHealthy(Path markerPath, long nowMillis, long maxAgeMillis) {
        if (markerPath == null || maxAgeMillis < 0) return false;
        try {
            if (!Files.isRegularFile(markerPath)) return false;
            FileTime lastModified = Files.getLastModifiedTime(markerPath);
            long ageMillis = nowMillis - lastModified.toMillis();
            return ageMillis >= 0 && ageMillis <= maxAgeMillis;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
