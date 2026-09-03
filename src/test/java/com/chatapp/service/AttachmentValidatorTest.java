package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Test
    void rejectsOrdinaryZipDeclaredAsDocx() throws Exception {
        byte[] zip = zipWithEntries("readme.txt");
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateContent(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zip));
    }

    @Test
    void acceptsMinimalDocxStructure() throws Exception {
        byte[] docx = zipWithEntries("[Content_Types].xml", "word/document.xml");
        AttachmentValidator.validateContent(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
    }

    @Test
    void rejectsOrdinaryZipDeclaredAsXlsx() throws Exception {
        byte[] zip = zipWithEntries("[Content_Types].xml", "word/document.xml");
        assertThrows(ValidationException.class,
                () -> AttachmentValidator.validateContent(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zip));
    }

    @Test
    void acceptsMinimalXlsxStructure() throws Exception {
        byte[] xlsx = zipWithEntries("[Content_Types].xml", "xl/workbook.xml");
        AttachmentValidator.validateContent(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
    }

    private static byte[] zipWithEntries(String... names) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("test".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
