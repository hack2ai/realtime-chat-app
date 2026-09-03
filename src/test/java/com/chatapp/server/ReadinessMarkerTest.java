package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void rejectsNullPath() {
        assertThrows(IllegalArgumentException.class, () -> new ReadinessMarker(null));
    }
}
