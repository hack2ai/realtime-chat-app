package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentStorageServiceTest {
    @Test
    void decodeBase64AcceptsMaximumDecodedSize() throws Exception {
        byte[] bytes = new byte[Math.toIntExact(AttachmentStorageService.MAX_FILE_BYTES)];
        String encoded = Base64.getEncoder().encodeToString(bytes);

        byte[] decoded = AttachmentStorageService.decodeBase64(encoded);

        assertArrayEquals(bytes, decoded);
    }

    @Test
    void decodeBase64RejectsDecodedSizeAboveLimit() {
        byte[] bytes = new byte[Math.toIntExact(AttachmentStorageService.MAX_FILE_BYTES + 1)];
        String encoded = Base64.getEncoder().encodeToString(bytes);

        assertThrows(ValidationException.class, () -> AttachmentStorageService.decodeBase64(encoded));
    }

    @Test
    void decodeBase64RejectsEncodedInputAboveLimitBeforeDecoding() {
        long maxBase64Chars = ((AttachmentStorageService.MAX_FILE_BYTES + 2) / 3) * 4;
        String oversized = "A".repeat(Math.toIntExact(maxBase64Chars + 1));

        assertThrows(ValidationException.class, () -> AttachmentStorageService.decodeBase64(oversized));
    }

    @Test
    void decodeBase64RejectsBlankInput() {
        assertThrows(ValidationException.class, () -> AttachmentStorageService.decodeBase64(" "));
    }

    @Test
    void decodeBase64RejectsMalformedInput() {
        assertThrows(ValidationException.class, () -> AttachmentStorageService.decodeBase64("not-base64!"));
    }

    @Test
    void cleanupOrphanedTempFilesRemovesOnlyStaleTempFiles() throws Exception {
        Path storageRoot = Files.createTempDirectory("chatapp-attachments-test-");
        try {
            Path staleTemp = storageRoot.resolve(".old-upload.tmp");
            Path freshTemp = storageRoot.resolve(".new-upload.tmp");
            Path ordinaryFile = storageRoot.resolve("regular.bin");
            Files.writeString(staleTemp, "stale");
            Files.writeString(freshTemp, "fresh");
            Files.writeString(ordinaryFile, "keep");

            Files.setLastModifiedTime(
                    staleTemp,
                    FileTime.from(Instant.now().minus(Duration.ofHours(2)))
            );

            AttachmentStorageService.cleanupOrphanedTempFiles(storageRoot);

            assertFalse(Files.exists(staleTemp));
            assertTrue(Files.exists(freshTemp));
            assertTrue(Files.exists(ordinaryFile));
        } finally {
            try (var files = Files.walk(storageRoot)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException ignored) {
                        // Best-effort cleanup for the isolated test directory.
                    }
                });
            }
        }
    }
}
