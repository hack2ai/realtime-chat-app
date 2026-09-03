package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentStorageServiceTest {
    @Test
    void decodeBase64AcceptsMaximumDecodedSize() throws Exception {
        byte[] bytes = new byte[(int) AttachmentStorageService.MAX_FILE_BYTES];
        String encoded = Base64.getEncoder().encodeToString(bytes);

        byte[] decoded = AttachmentStorageService.decodeBase64(encoded);

        assertArrayEquals(bytes, decoded);
    }

    @Test
    void decodeBase64RejectsDecodedSizeAboveLimit() {
        byte[] bytes = new byte[(int) AttachmentStorageService.MAX_FILE_BYTES + 1];
        String encoded = Base64.getEncoder().encodeToString(bytes);

        assertThrows(ValidationException.class, () -> AttachmentStorageService.decodeBase64(encoded));
    }

    @Test
    void decodeBase64RejectsEncodedInputAboveLimitBeforeDecoding() {
        String oversized = "A".repeat(((AttachmentStorageService.MAX_FILE_BYTES + 2) / 3) * 4 + 1);

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
}
