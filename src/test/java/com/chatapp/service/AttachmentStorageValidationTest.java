package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentStorageValidationTest {
    @Test
    void rejectsUnsupportedContentType() {
        AttachmentStorageService storage = new AttachmentStorageService();
        assertThrows(ValidationException.class, () -> storage.store("payload.exe", "application/x-msdownload", new byte[]{1, 2, 3}));
    }

    @Test
    void rejectsMismatchedPngSignature() {
        AttachmentStorageService storage = new AttachmentStorageService();
        assertThrows(ValidationException.class, () -> storage.store("image.png", "image/png", new byte[]{1, 2, 3}));
    }

    @Test
    void acceptsPlainTextWithoutBinarySignature() throws ValidationException {
        AttachmentStorageService storage = new AttachmentStorageService();
        byte[] source = "hello chat".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AttachmentStorageService.StoredFile stored = storage.store("hello.txt", "text/plain", source);
        try {
            assertArrayEquals(source, storage.load(stored.fileId()));
        } finally {
            storage.delete(stored.fileId());
        }
    }

    @Test
    void rejectsMalformedBase64() {
        assertThrows(ValidationException.class, () -> AttachmentStorageService.decodeBase64("not-base64!!!"));
    }

    @Test
    void roundTripsBase64() throws ValidationException {
        byte[] source = "safe attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(source, AttachmentStorageService.decodeBase64(Base64.getEncoder().encodeToString(source)));
    }
}
