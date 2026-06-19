package com.chatapp.exception;

/**
 * Thrown when user-supplied input fails validation (e.g. invalid email
 * format, password too short, username already taken at the DB
 * constraint level).
 *
 * <p>Deliberately a checked exception: validation failure is an
 * expected, recoverable outcome of normal operation (a user mistyping
 * their email is not exceptional in the same sense as a database
 * connection dying), so callers are required to consciously handle it
 * and turn it into a {@code S2C_REGISTER_FAILED} / {@code S2C_LOGIN_FAILED}
 * response rather than letting it propagate as an unchecked surprise.
 */
public class ValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
