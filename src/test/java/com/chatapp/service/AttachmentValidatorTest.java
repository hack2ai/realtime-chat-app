package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentValidatorTest {
    @Test
    void stripsPathTraversalFromFileName() throws Exception {
        assertEquals("photo.png", AttachmentValidator.validateAndNormalizeName("../../photo.png"));
        assertEquals("secret.txt", AttachmentValidator.validateAndNormalizeName("..\\secret.txt"));
    }

    @Test
    void rejectsExecutableExtensions() {
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateAndNormalizeName("invoice.exe"));
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateAndNormalizeName("script.PS1"));
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateAndNormalizeType("application/x-msdownload"));
    }

    @Test
    void rejectsSpoofedPng() throws Exception {
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateContent("image/png", "not a png".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsValidPdfSignature() throws Exception {
        AttachmentValidator.validateContent("application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void acceptsGenericBinaryWhenNoReliableSignatureExists() throws Exception {
        AttachmentValidator.validateContent("application/octet-stream", new byte[]{1, 2, 3, 4});
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateContent("text/plain", new byte[0]));
    }
}
