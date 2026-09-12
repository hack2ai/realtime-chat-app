package com.chatapp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentStorageCleanupTest {
    @TempDir
    Path storageRoot;

    @Test
    void cleanupRemovesOnlyOldTempFiles() throws Exception {
        Path oldTemp = storageRoot.resolve(".old-upload.tmp");
        Path freshTemp = storageRoot.resolve(".active-upload.tmp");
        Path unrelated = storageRoot.resolve("keep.bin");
        Files.write(oldTemp, new byte[]{1});
        Files.write(freshTemp, new byte[]{2});
        Files.write(unrelated, new byte[]{3});

        FileTime oldTime = FileTime.from(Instant.now().minus(Duration.ofHours(2)));
        Files.setLastModifiedTime(oldTemp, oldTime);

        AttachmentStorageService.cleanupOrphanedTempFiles(storageRoot);

        assertFalse(Files.exists(oldTemp));
        assertTrue(Files.exists(freshTemp));
        assertTrue(Files.exists(unrelated));
    }
}
