package com.chatapp.util;

import com.chatapp.exception.ValidationException;

import java.util.regex.Pattern;

/** Centralized server-side input validation rules. */
public final class ValidationUtil {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_GROUP_NAME_LENGTH = 80;

    private ValidationUtil() {
    }

    public static void validateUsername(String username) throws ValidationException {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username is required.");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ValidationException(
                    "Username must be 3-30 characters and contain only letters, numbers, and underscores."
            );
        }
    }

    public static void validateEmail(String email) throws ValidationException {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email is required.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Email address is not valid.");
        }
    }

    /** Validates password strength; AuthenticationService additionally enforces bcrypt's 72-byte input boundary. */
    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password is required.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("Password must not exceed " + MAX_PASSWORD_LENGTH + " characters.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("Password must contain at least one letter and one number.");
        }
    }

    public static void validatePasswordsMatch(String password, String confirmPassword) throws ValidationException {
        if (password == null || !password.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
    }

    public static void validateMessageContent(String message) throws ValidationException {
        if (message == null || message.isBlank()) {
            throw new ValidationException("Message cannot be empty.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ValidationException(
                    "Message exceeds maximum length of " + MAX_MESSAGE_LENGTH + " characters."
            );
        }
    }

    public static void validateGroupName(String groupName) throws ValidationException {
        if (groupName == null || groupName.isBlank()) {
            throw new ValidationException("Group name is required.");
        }
        if (groupName.length() > MAX_GROUP_NAME_LENGTH) {
            throw new ValidationException(
                    "Group name must not exceed " + MAX_GROUP_NAME_LENGTH + " characters."
            );
        }
    }
}
