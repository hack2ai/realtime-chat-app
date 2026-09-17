package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerHealthCheckTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsFreshReadinessMarker() throws Exception {
        Path marker = tempDir.resolve("ready.marker");
        Files.createFile(marker);
        long modifiedAt = Files.getLastModifiedTime(marker).toMillis();

        assertTrue(ServerHealthCheck.isHealthy(marker, modifiedAt + 5_000, 30_000));
    }

    @Test
    void acceptsMarkerExactlyAtAgeLimit() throws Exception {
        Path marker = tempDir.resolve("ready.marker");
        Files.createFile(marker);
        long modifiedAt = Files.getLastModifiedTime(marker).toMillis();

        assertTrue(ServerHealthCheck.isHealthy(marker, modifiedAt + 30_000, 30_000));
    }

    @Test
    void rejectsStaleReadinessMarker() throws Exception {
        Path marker = tempDir.resolve("ready.marker");
        Files.createFile(marker);
        long modifiedAt = Files.getLastModifiedTime(marker).toMillis();

        assertFalse(ServerHealthCheck.isHealthy(marker, modifiedAt + 30_001, 30_000));
    }

    @Test
    void rejectsFutureDatedReadinessMarker() throws Exception {
        Path marker = tempDir.resolve("ready.marker");
        Files.createFile(marker);
        long now = Files.getLastModifiedTime(marker).toMillis();
        Files.setLastModifiedTime(marker, FileTime.fromMillis(now + 5_000));

        assertFalse(ServerHealthCheck.isHealthy(marker, now, 30_000));
    }

    @Test
    void rejectsDirectoryAtReadinessMarkerPath() throws Exception {
        Path marker = tempDir.resolve("ready.marker");
        Files.createDirectory(marker);

        assertFalse(ServerHealthCheck.isHealthy(marker, System.currentTimeMillis(), 30_000));
    }

    @Test
    void rejectsMissingReadinessMarker() {
        assertFalse(ServerHealthCheck.isHealthy(tempDir.resolve("missing.marker"), System.currentTimeMillis(), 30_000));
    }

    @Test
    void rejectsInvalidInputs() throws Exception {
        Path marker = tempDir.resolve("ready.marker");
        Files.createFile(marker);
        long modifiedAt = Files.getLastModifiedTime(marker).toMillis();

        assertFalse(ServerHealthCheck.isHealthy(null, modifiedAt, 30_000));
        assertFalse(ServerHealthCheck.isHealthy(marker, modifiedAt, -1));
        assertFalse(ServerHealthCheck.isHealthy(marker, modifiedAt - 1, 30_000));
    }
}
