package com.chatapp.util;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilTest {

    @Test
    void acceptsValidUsernameAndEmail() {
        assertDoesNotThrow(() -> ValidationUtil.validateUsername("chat_user_123"));
        assertDoesNotThrow(() -> ValidationUtil.validateEmail("user@example.com"));
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
    void acceptsPasswordAtBoundaryButRejectsOversizedInput() {
        assertDoesNotThrow(() -> ValidationUtil.validatePassword("a1" + "x".repeat(6)));
        assertDoesNotThrow(() -> ValidationUtil.validatePassword("a1" + "x".repeat(126)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validatePassword("a1" + "x".repeat(127)));
    }

    @Test
    void validatesMessageAndGroupNameBoundaries() {
        assertDoesNotThrow(() -> ValidationUtil.validateMessageContent("a".repeat(4000)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMessageContent("a".repeat(4001)));
        assertDoesNotThrow(() -> ValidationUtil.validateGroupName("g".repeat(80)));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateGroupName("g".repeat(81)));
    }
}
