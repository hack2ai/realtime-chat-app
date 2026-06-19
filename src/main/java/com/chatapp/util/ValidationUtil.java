package com.chatapp.util;

import com.chatapp.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Centralized input validation rules for user-supplied data.
 *
 * <p>Kept as one static utility class so the rules are defined exactly
 * once. If validation logic were duplicated between, say, a client-side
 * "disable submit button" check and a server-side check, the two could
 * drift apart over time (e.g. someone loosens the client check for UX
 * reasons and forgets the server still enforces the old, stricter
 * rule) — both should call into this same class.
 *
 * <p>Server-side validation here is mandatory and authoritative
 * regardless of what the client does: a client check is just a UX nicety,
 * since nothing stops a custom/modified client (or someone testing with
 * raw sockets) from sending unvalidated data directly. The
 * {@code AuthenticationService} always re-validates server-side before
 * touching the database.
 */
public final class ValidationUtil {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,30}$");

    // Pragmatic email check: not a full RFC 5322 implementation (which
    // is enormous and still lets through addresses that don't deliver),
    // just "looks roughly like an email and won't crash downstream code".
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128; // bcrypt has a 72-byte input limit; see note below
    private static final int MAX_MESSAGE_LENGTH = 5000;
    private static final int MAX_GROUP_NAME_LENGTH = 50;

    private ValidationUtil() {
    }

    /**
     * Validates a username: 3-30 characters, alphanumeric plus
     * underscore only. Rejects emoji/unicode in usernames deliberately —
     * usernames are used in places (mentions, search) where exotic
     * unicode causes more problems (lookalike-character spoofing,
     * rendering inconsistencies) than it solves.
     */
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

    /**
     * Validates password strength.
     *
     * <p>Note on the max length: bcrypt silently truncates its input at
     * 72 bytes — anything beyond that contributes nothing to the hash.
     * A user who sets a 200-character password would, unknown to them,
     * actually only have the first ~72 bytes checked, which is a subtle
     * footgun. Capping at 128 characters here is a usability/sanity
     * limit; the actual bcrypt truncation point is enforced/explained
     * in {@code AuthenticationService}.
     */
    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password is required.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters."
            );
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password must not exceed " + MAX_PASSWORD_LENGTH + " characters."
            );
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

    /**
     * Validates message content is non-empty and within the size limit.
     * Used by both private and group messaging (Phase 2/3) so the same
     * rule applies everywhere a free-text message is sent.
     */
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
