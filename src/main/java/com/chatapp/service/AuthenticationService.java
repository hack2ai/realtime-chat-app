package com.chatapp.service;

import com.chatapp.config.AppConfig;
import com.chatapp.database.UserDAO;
import com.chatapp.exception.AuthenticationException;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.User;
import com.chatapp.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles registration, login, and session-token lifecycle.
 *
 * <h2>Password handling</h2>
 * Passwords are hashed with bcrypt ({@link BCryptPasswordEncoder}) before
 * ever touching the database. Bcrypt is used rather than a general-purpose
 * hash like SHA-256 because bcrypt is deliberately slow and has a tunable
 * work factor — this matters specifically because it makes brute-force /
 * rainbow-table attacks on a leaked password database computationally
 * expensive, which a fast hash does not provide. Salting is automatic and
 * built into the bcrypt output string, so no separate salt column is needed.
 *
 * <p>Plaintext passwords exist in memory only for the brief moment between
 * being read off the socket and being passed to {@code encoder.encode(...)}
 * or {@code encoder.matches(...)} — they are never logged, never stored in
 * a field on this class, and never written anywhere.
 *
 * <h2>Session tokens</h2>
 * On successful login, a random opaque session token is generated and
 * held in an in-memory map alongside the user ID and an expiry time. This
 * is intentionally simple for Phase 1: a real production system would
 * likely persist sessions (so they survive a server restart) and might
 * use signed JWTs instead of a server-side lookup table. For a
 * single-server socket application, an in-memory map keyed by token is
 * sufficient and avoids a database round-trip on every authenticated
 * request.
 */
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserDAO userDAO;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /** token -> session record. Cleared lazily on lookup if expired. */
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    public AuthenticationService() {
        this.userDAO = new UserDAO();
        this.passwordEncoder = new BCryptPasswordEncoder(AppConfig.getBcryptStrength());
    }

    /** Constructor allowing DAO injection, for unit testing with a fake/mock DAO. */
    public AuthenticationService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.passwordEncoder = new BCryptPasswordEncoder(AppConfig.getBcryptStrength());
    }

    // ---------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------

    /**
     * Validates and registers a new user.
     *
     * @throws ValidationException if any field fails validation, or if
     *         the username/email is already taken. Deliberately a
     *         {@link ValidationException} (not {@link AuthenticationException})
     *         since "username taken" is a registration-input problem,
     *         not a credentials problem.
     */
    public User register(String username, String email, String password, String confirmPassword)
            throws ValidationException {

        ValidationUtil.validateUsername(username);
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePassword(password);
        ValidationUtil.validatePasswordsMatch(password, confirmPassword);

        // bcrypt only uses the first 72 BYTES of input; warn-log (not
        // reject) if a password would be silently truncated, since this
        // is a real correctness gotcha that's easy to miss. Most realistic
        // passwords are well under this, so this rarely fires in practice.
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            logger.warn(
                    "Password for username '{}' exceeds bcrypt's 72-byte input limit; " +
                    "only the first 72 bytes will actually be used by the hash.",
                    username
            );
        }

        if (userDAO.usernameExists(username)) {
            throw new ValidationException("Username '" + username + "' is already taken.");
        }
        if (userDAO.emailExists(email)) {
            throw new ValidationException("An account with this email already exists.");
        }

        String hash = passwordEncoder.encode(password);
        User newUser = new User(username, email, hash);
        User saved = userDAO.insert(newUser);

        logger.info("New user registered: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    // ---------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------

    /**
     * Authenticates a user and, on success, issues a new session token.
     *
     * @throws AuthenticationException with a deliberately generic
     *         message — "Invalid username/email or password." — in
     *         every failure case (unknown identifier OR wrong password).
     *         Distinguishing these in the error message would let an
     *         attacker enumerate which usernames/emails exist in the
     *         system by observing which error they get back; this is a
     *         well-known anti-pattern this method deliberately avoids.
     */
    public LoginResult login(String usernameOrEmail, String password) throws AuthenticationException {
        final String genericFailureMessage = "Invalid username/email or password.";

        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationException(genericFailureMessage);
        }

        User user = userDAO.findByUsernameOrEmail(usernameOrEmail).orElse(null);

        // Deliberately still run a bcrypt comparison even when no user
        // was found, against a dummy hash, so that the response time
        // for "unknown user" and "wrong password" are similar. Returning
        // immediately on a missing user would make the login endpoint
        // measurably faster for nonexistent accounts than for existing
        // ones, which is a timing side-channel an attacker could use to
        // enumerate valid usernames even with an identical error message.
        String hashToCheck = (user != null)
                ? user.getPasswordHash()
                : "$2a$12$invalidsaltinvalidsaltinvalidsaltuv9bL.J6/PqcG1xX5/SXa"; // structurally valid bcrypt hash, matches nothing

        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);

        if (user == null || !passwordMatches) {
            logger.info("Failed login attempt for identifier: {}", usernameOrEmail);
            throw new AuthenticationException(genericFailureMessage);
        }

        String token = generateSessionToken();
        LocalDateTime expiry = LocalDateTime.now().plusHours(AppConfig.getSessionExpiryHours());
        activeSessions.put(token, new Session(user.getId(), expiry));

        userDAO.updateStatus(user.getId(), User.Status.ONLINE);
        logger.info("User logged in: id={}, username={}", user.getId(), user.getUsername());

        return new LoginResult(user, token);
    }

    /**
     * Invalidates a session token (logout) and marks the associated
     * user offline with an updated last-seen timestamp.
     */
    public void logout(String sessionToken) {
        Session session = activeSessions.remove(sessionToken);
        if (session != null) {
            userDAO.updateStatus(session.userId, User.Status.OFFLINE);
            userDAO.updateLastSeen(session.userId, LocalDateTime.now());
            logger.info("User logged out: userId={}", session.userId);
        }
    }

    /**
     * Validates a session token and returns the associated user ID, or
     * throws if the token is unknown or expired.
     *
     * <p>Expired sessions are removed from the map on lookup (lazy
     * cleanup) rather than via a separate background sweep thread — for
     * the expected session volume of a project like this, a background
     * sweeper would be unneeded complexity; the cost of checking
     * expiry on each lookup is negligible.
     */
    public int validateSession(String sessionToken) throws AuthenticationException {
        Session session = activeSessions.get(sessionToken);
        if (session == null) {
            throw new AuthenticationException("Session is invalid or has expired. Please log in again.");
        }
        if (LocalDateTime.now().isAfter(session.expiresAt)) {
            activeSessions.remove(sessionToken);
            throw new AuthenticationException("Session is invalid or has expired. Please log in again.");
        }
        return session.userId;
    }

    /**
     * Generates a cryptographically random, URL-safe session token.
     * 32 bytes of entropy (256 bits) — far beyond what's brute-forceable,
     * sourced from {@link SecureRandom} rather than {@link java.util.Random},
     * since the latter is predictable and unsuitable for anything
     * security-sensitive.
     */
    private String generateSessionToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** Internal session record. */
    private record Session(int userId, LocalDateTime expiresAt) {
    }

    /** Result of a successful login: the authenticated user plus their new session token. */
    public record LoginResult(User user, String sessionToken) {
    }
}
