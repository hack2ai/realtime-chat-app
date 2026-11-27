package com.chatapp.exception;

/**
 * Thrown when an authentication attempt fails for credential reasons:
 * wrong password, unknown username/email, or an expired/invalid
 * session token.
 *
 * <p>Kept distinct from {@link ValidationException} because the two
 * have different security implications for how callers should respond:
 * a {@code ValidationException} (e.g. "email format invalid") is safe
 * to report back to the client with a specific reason. An
 * {@code AuthenticationException} for login should generally be
 * reported back with a deliberately generic message ("invalid username
 * or password") regardless of whether the username didn't exist or the
 * password was wrong — revealing which one it was lets an attacker
 * enumerate valid usernames. See
 * {@code AuthenticationService#login} for where this distinction is
 * enforced.
 */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
