package com.chatapp.service;

import com.chatapp.exception.ValidationException;

import java.util.Base64;

/** Storage boundary for authenticated chat attachments. */
public interface AttachmentStorage {
    long MAX_FILE_BYTES = 5L * 1024 * 1024;
    long MAX_BASE64_CHARS = ((MAX_FILE_BYTES + 2) / 3) * 4;

    record StoredFile(String fileId, String fileName, String contentType, long sizeBytes, String sha256) {}

    StoredFile store(String fileName, String contentType, byte[] bytes) throws ValidationException;

    byte[] load(String fileId) throws ValidationException;

    void delete(String fileId);

    static byte[] decodeBase64(String data) throws ValidationException {
        if (data == null || data.isBlank()) throw new ValidationException("File data is required.");
        if (data.length() > MAX_BASE64_CHARS) throw new ValidationException("File exceeds the 5 MB limit.");
        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            if (decoded.length > MAX_FILE_BYTES) throw new ValidationException("File exceeds the 5 MB limit.");
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new ValidationException("File data is not valid Base64.", e);
        }
    }
}
