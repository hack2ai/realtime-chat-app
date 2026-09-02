package com.chatapp.service;

import com.chatapp.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Validates attachment metadata and file signatures before storage. */
public final class AttachmentValidator {
    private static final int MAX_NAME_LENGTH = 180;
    private static final int MAX_CONTENT_TYPE_LENGTH = 120;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv", "application/octet-stream",
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "audio/mpeg", "audio/wav", "video/mp4", "application/zip", "application/json",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "com", "bat", "cmd", "ps1", "sh", "bash", "zsh", "jar", "class",
            "msi", "scr", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "cpl", "apk", "appimage"
    );

    private AttachmentValidator() {}

    public static String validateAndNormalizeName(String fileName) throws ValidationException {
        if (fileName == null || fileName.isBlank()) throw new ValidationException("File name is required.");
        String name = fileName.strip().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) throw new ValidationException("Invalid file name.");
        if (isBlockedExtension(name)) throw new ValidationException("Executable attachment types are not allowed.");
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public static String validateAndNormalizeType(String value) throws ValidationException {
        String type = value == null || value.isBlank() ? "application/octet-stream" : value.strip().toLowerCase(Locale.ROOT);
        if (type.length() > MAX_CONTENT_TYPE_LENGTH || !ALLOWED_TYPES.contains(type)) {
            throw new ValidationException("Unsupported attachment type.");
        }
        return type;
    }

    public static void validateContent(String type, byte[] bytes) throws ValidationException {
        if (bytes == null || bytes.length == 0) throw new ValidationException("File is empty.");
        switch (type) {
            case "image/png" -> require(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47}, "PNG");
            case "image/jpeg" -> require(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "JPEG");
            case "image/gif" -> require(bytes, ascii("GIF8"), "GIF");
            case "image/webp" -> requireWebp(bytes);
            case "application/pdf" -> require(bytes, ascii("%PDF-"), "PDF");
            case "application/zip",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> requireZip(bytes);
            case "audio/wav" -> requireRiff(bytes, "WAVE");
            case "video/mp4" -> requireMp4(bytes);
            case "audio/mpeg" -> requireMpeg(bytes);
            case "application/json", "text/plain", "text/csv", "application/octet-stream" -> { /* no reliable fixed signature */ }
            default -> throw new ValidationException("Unsupported attachment type.");
        }
    }

    private static boolean isBlockedExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        return BLOCKED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void require(byte[] value, byte[] prefix, String type) throws ValidationException {
        if (!startsWith(value, prefix)) throw new ValidationException("File content does not match its declared type: " + type + ".");
    }

    private static void requireZip(byte[] value) throws ValidationException {
        if (!(startsWith(value, new byte[]{0x50, 0x4b, 0x03, 0x04})
                || startsWith(value, new byte[]{0x50, 0x4b, 0x05, 0x06})
                || startsWith(value, new byte[]{0x50, 0x4b, 0x07, 0x08}))) {
            throw new ValidationException("File content does not match its declared ZIP-based type.");
        }
    }

    private static void requireWebp(byte[] value) throws ValidationException {
        if (value.length < 12 || !startsWith(value, ascii("RIFF")) || !startsWithAt(value, ascii("WEBP"), 8)) {
            throw new ValidationException("File content does not match its declared WebP type.");
        }
    }

    private static void requireRiff(byte[] value, String format) throws ValidationException {
        if (value.length < 12 || !startsWith(value, ascii("RIFF")) || !startsWithAt(value, ascii(format), 8)) {
            throw new ValidationException("File content does not match its declared type.");
        }
    }

    private static void requireMp4(byte[] value) throws ValidationException {
        if (value.length < 12 || !startsWithAt(value, ascii("ftyp"), 4)) {
            throw new ValidationException("File content does not match its declared MP4 type.");
        }
    }

    private static void requireMpeg(byte[] value) throws ValidationException {
        boolean id3 = startsWith(value, ascii("ID3"));
        boolean frame = value.length >= 2 && (value[0] & 0xff) == 0xff && (value[1] & 0xe0) == 0xe0;
        if (!id3 && !frame) throw new ValidationException("File content does not match its declared MPEG type.");
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static boolean startsWith(byte[] value, byte[] prefix) { return startsWithAt(value, prefix, 0); }
    private static boolean startsWithAt(byte[] value, byte[] prefix, int offset) {
        if (offset < 0 || value.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[offset + i] != prefix[i]) return false;
        return true;
    }
}
