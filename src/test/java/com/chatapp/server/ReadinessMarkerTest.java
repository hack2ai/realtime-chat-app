package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadinessMarkerTest {
    @TempDir
    Path tempDir;

    @Test
    void markCreatesMarkerAndClearRemovesIt() throws Exception {
        Path markerPath = tempDir.resolve("ready.marker");
        ReadinessMarker marker = new ReadinessMarker(markerPath);

        marker.markReady();
        assertTrue(Files.isRegularFile(markerPath));

        marker.clear();
        assertFalse(Files.exists(markerPath));
    }

    @Test
    void markReplacesStaleMarker() throws Exception {
        Path markerPath = tempDir.resolve("ready.marker");
        Files.createDirectories(markerPath.getParent());
        Files.writeString(markerPath, "stale");
        ReadinessMarker marker = new ReadinessMarker(markerPath);

        marker.markReady();

        assertTrue(Files.isRegularFile(markerPath));
        assertEquals(0, Files.size(markerPath));
    }

    @Test
    void heartbeatRefreshesMarkerTimestamp() throws Exception {
        Path markerPath = tempDir.resolve("ready.marker");
        ReadinessMarker marker = new ReadinessMarker(markerPath);

        marker.markReady();
        Files.setLastModifiedTime(markerPath, FileTime.fromMillis(1));

        marker.heartbeat();

        assertTrue(Files.getLastModifiedTime(markerPath).toMillis() > 1);
    }

    @Test
    void heartbeatRejectsSymlinkMarker() throws Exception {
        Path target = tempDir.resolve("target");
        Path markerPath = tempDir.resolve("ready.marker");
        Files.createFile(target);
        boolean created = false;
        try {
            Files.createSymbolicLink(markerPath, target.getFileName());
            created = true;
        } catch (UnsupportedOperationException | SecurityException e) {
            // Symlink creation is optional on restricted development environments.
        } catch (java.io.IOException e) {
            // Some platforms require elevated permissions for symlink creation.
        }
        Assumptions.assumeTrue(created, "symbolic links are not available in this environment");

        ReadinessMarker marker = new ReadinessMarker(markerPath);

        assertThrows(java.io.IOException.class, marker::heartbeat);
    }

    @Test
    void rejectsNullPath() {
        assertThrows(IllegalArgumentException.class, () -> new ReadinessMarker(null));
    }
}
