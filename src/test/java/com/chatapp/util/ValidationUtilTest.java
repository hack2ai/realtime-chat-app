package com.chatapp.util;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilTest {

    @Test
    void acceptsValidUsernameAndEmail() {
        assertDoesNotThrow(() -> ValidationUtil.validateUsername("chat_user_123"));
        assertDoesNotThrow(() -> ValidationUtil.validateEmail("user@example.com"));
    }

    @Test
    void rejectsMissingIdentityFields() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateUsername(null));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateUsername("   "));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail(null));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("   "));
    }

    @Test
    void enforcesUsernameBoundariesAndCharacterSet() {
        assertDoesNotThrow(() -> ValidationUtil.validateUsername("abc"));
        assertDoesNotThrow(() -> ValidationUtil.validateUsername("a".repeat(30)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateUsername("ab"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateUsername("a".repeat(31)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateUsername("chat-user"));
    }

    @Test
    void rejectsMalformedEmailAddresses() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("user"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("user@"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("@example.com"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("user@example"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateEmail("user example@example.com"));
    }

    @Test
    void rejectsUnicodeLookalikeUsername() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateUsername("usеr123"));
    }

    @Test
    void rejectsPasswordWithoutLetterAndDigit() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassword("12345678"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassword("abcdefgh"));
    }

    @Test
    void validatesPasswordConfirmation() {
        assertDoesNotThrow(() -> ValidationUtil.validatePasswordsMatch("Secret123", "Secret123"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePasswordsMatch("Secret123", "Secret124"));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePasswordsMatch(null, "Secret123"));
    }

    @Test
    void enforcesPasswordUtf8ByteBoundary() {
        String validAscii = "a1" + "x".repeat(70);
        String oversizedAscii = validAscii + "x";
        String validMultibyte = "A1" + "é".repeat(35);
        String oversizedMultibyte = "A1" + "é".repeat(36);

        assertEquals(72, validAscii.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(73, oversizedAscii.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(72, validMultibyte.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(74, oversizedMultibyte.getBytes(StandardCharsets.UTF_8).length);

        assertDoesNotThrow(() -> ValidationUtil.validatePassword(validAscii));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassword(oversizedAscii));
        assertDoesNotThrow(() -> ValidationUtil.validatePassword(validMultibyte));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassword(oversizedMultibyte));
    }

    @Test
    void validatesMessageAndGroupNameBoundaries() {
        assertDoesNotThrow(() -> ValidationUtil.validateMessageContent("a".repeat(4000)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMessageContent("a".repeat(4001)));
        assertDoesNotThrow(() -> ValidationUtil.validateGroupName("g".repeat(80)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateGroupName("g".repeat(81)));
    }
}
