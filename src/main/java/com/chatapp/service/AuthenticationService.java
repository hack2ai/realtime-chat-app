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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** Handles registration, login, and server-side session lifecycle. */
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String GENERIC_LOGIN_FAILURE = "Invalid username/email or password.";
    private static final String DUMMY_BCRYPT_HASH =
            "$2y$12$vgm76N96ItnRWvltvIMMReV0FQkritT0LtRtzB/U4fHvqV.aYVY.O";
    private static final int MAX_LOGIN_IDENTIFIER_LENGTH = 254;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    private static final int MAX_SESSION_TOKEN_LENGTH = 128;
    private static final long SESSION_CLEANUP_INTERVAL_NANOS = Duration.ofMinutes(5).toNanos();

    private final UserDAO userDAO;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    /** Stores only SHA-256 digests of bearer tokens, never the raw tokens. */
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final Map<Integer, String> activeTokenByUser = new ConcurrentHashMap<>();
    /** Serializes session/status transitions for the same user without blocking unrelated accounts. */
    private final Map<Integer, SessionLock> sessionLocks = new ConcurrentHashMap<>();
    /** Rate-limits expiry scans without creating a background maintenance thread. */
    private final AtomicLong nextSessionCleanupAtNanos = new AtomicLong(0L);

    public AuthenticationService() {
        this.userDAO = new UserDAO();
        this.passwordEncoder = new BCryptPasswordEncoder(AppConfig.getBcryptStrength());
    }

    public AuthenticationService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.passwordEncoder = new BCryptPasswordEncoder(AppConfig.getBcryptStrength());
    }

    public User register(String username, String email, String password, String confirmPassword) throws ValidationException {
        ValidationUtil.validateUsername(username);
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePassword(password);
        ValidationUtil.validatePasswordsMatch(password, confirmPassword);
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new ValidationException("Password must not exceed 72 UTF-8 bytes.");
        }
        if (userDAO.usernameExists(username)) throw new ValidationException("Username '" + username + "' is already taken.");
        if (userDAO.emailExists(email)) throw new ValidationException("An account with this email already exists.");

        User candidate = new User(username, email, passwordEncoder.encode(password));
        try {
            User saved = userDAO.insert(candidate);
            logger.info("New user registered: id={}", saved.getId());
            return saved;
        } catch (RuntimeException e) {
            if (isDuplicateKeyViolation(e)) {
                throw duplicateRegistrationFailure(username, email);
            }
            throw e;
        }
    }

    private ValidationException duplicateRegistrationFailure(String username, String email) {
        if (userDAO.usernameExists(username)) {
            return new ValidationException("Username '" + username + "' is already taken.");
        }
        if (userDAO.emailExists(email)) {
            return new ValidationException("An account with this email already exists.");
        }
        return new ValidationException("An account with these details already exists.");
    }

    private boolean isDuplicateKeyViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.sql.SQLIntegrityConstraintViolationException) return true;
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("duplicate") || normalized.contains("unique constraint")
                        || normalized.contains("duplicate entry") || normalized.contains("unique index")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public LoginResult login(String usernameOrEmail, String password) throws AuthenticationException {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isEmpty()) {
            runDummyPasswordCheck(password);
            throw new AuthenticationException(GENERIC_LOGIN_FAILURE);
        }

        String identifier = usernameOrEmail.strip();
        if (identifier.length() > MAX_LOGIN_IDENTIFIER_LENGTH) {
            runDummyPasswordCheck(password);
            throw new AuthenticationException(GENERIC_LOGIN_FAILURE);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            runDummyPasswordCheck("password");
            throw new AuthenticationException(GENERIC_LOGIN_FAILURE);
        }

        User user = userDAO.findByUsernameOrEmail(identifier).orElse(null);
        String hashToCheck = user == null ? DUMMY_BCRYPT_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);
        if (user == null || !passwordMatches) {
            logger.info("Failed login attempt.");
            throw new AuthenticationException(GENERIC_LOGIN_FAILURE);
        }

        maybeCleanupExpiredSessions();

        SessionLock sessionLock = acquireSessionLock(user.getId());
        try {
            releaseExpiredSession(user.getId(), LocalDateTime.now());

            String token = generateSessionToken();
            String tokenDigest = digestToken(token);
            LocalDateTime expiry = LocalDateTime.now().plusHours(AppConfig.getSessionExpiryHours());
            Session session = new Session(user.getId(), expiry);
            if (activeTokenByUser.putIfAbsent(user.getId(), tokenDigest) != null) {
                throw new AuthenticationException("This account is already connected.");
            }
            activeSessions.put(tokenDigest, session);
            try {
                userDAO.updateStatus(user.getId(), User.Status.ONLINE);
            } catch (RuntimeException e) {
                activeSessions.remove(tokenDigest, session);
                activeTokenByUser.remove(user.getId(), tokenDigest);
                throw e;
            }
            return new LoginResult(user, token, expiry);
        } finally {
            releaseSessionLock(user.getId(), sessionLock);
        }
    }

    private void runDummyPasswordCheck(String suppliedPassword) {
        String safePassword = suppliedPassword == null ? "" : suppliedPassword;
        if (safePassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            safePassword = "password";
        }
        passwordEncoder.matches(safePassword, DUMMY_BCRYPT_HASH);
    }

    public void logout(String sessionToken) {
        if (sessionToken == null || sessionToken.length() > MAX_SESSION_TOKEN_LENGTH) return;
        String tokenDigest = digestToken(sessionToken);
        Session session = activeSessions.get(tokenDigest);
        if (session == null) return;

        SessionLock sessionLock = acquireSessionLock(session.userId);
        try {
            Session current = activeSessions.remove(tokenDigest);
            if (current != null) {
                activeTokenByUser.remove(current.userId, tokenDigest);
                markOfflineSafely(current.userId);
            }
        } finally {
            releaseSessionLock(session.userId, sessionLock);
        }
    }

    public int validateSession(String sessionToken) throws AuthenticationException {
        if (sessionToken == null || sessionToken.isBlank() || sessionToken.length() > MAX_SESSION_TOKEN_LENGTH) {
            throw invalidSession();
        }
        maybeCleanupExpiredSessions();
        String tokenDigest = digestToken(sessionToken);
        Session session = activeSessions.get(tokenDigest);
        if (session == null) throw invalidSession();

        SessionLock sessionLock = acquireSessionLock(session.userId);
        try {
            Session current = activeSessions.get(tokenDigest);
            if (current == null || current != session) throw invalidSession();
            if (!LocalDateTime.now().isBefore(current.expiresAt)) {
                expireSessionLocked(tokenDigest, current);
                throw invalidSession();
            }
            return current.userId;
        } finally {
            releaseSessionLock(session.userId, sessionLock);
        }
    }

    private void maybeCleanupExpiredSessions() {
        long nowNanos = System.nanoTime();
        long nextCleanup = nextSessionCleanupAtNanos.get();
        if (nowNanos < nextCleanup
                || !nextSessionCleanupAtNanos.compareAndSet(nextCleanup, nowNanos + SESSION_CLEANUP_INTERVAL_NANOS)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        activeSessions.forEach((tokenDigest, session) -> {
            if (!now.isBefore(session.expiresAt)) {
                SessionLock sessionLock = acquireSessionLock(session.userId);
                try {
                    expireSessionLocked(tokenDigest, session);
                } finally {
                    releaseSessionLock(session.userId, sessionLock);
                }
            }
        });
    }

    private void releaseExpiredSession(int userId, LocalDateTime now) {
        String tokenDigest = activeTokenByUser.get(userId);
        if (tokenDigest == null) return;

        Session session = activeSessions.get(tokenDigest);
        if (session == null) {
            activeTokenByUser.remove(userId, tokenDigest);
            return;
        }
        if (!now.isBefore(session.expiresAt)) {
            expireSession(tokenDigest, session);
        }
    }

    private void expireSessionLocked(String tokenDigest, Session session) {
        if (activeSessions.remove(tokenDigest, session)) {
            activeTokenByUser.remove(session.userId, tokenDigest);
            markOfflineSafely(session.userId);
        }
    }

    private SessionLock acquireSessionLock(int userId) {
        SessionLock sessionLock = sessionLocks.compute(userId, (key, existing) -> {
            SessionLock selected = existing == null ? new SessionLock() : existing;
            selected.references++;
            return selected;
        });
        sessionLock.lock.lock();
        return sessionLock;
    }

    private void releaseSessionLock(int userId, SessionLock sessionLock) {
        sessionLock.lock.unlock();
        sessionLocks.computeIfPresent(userId, (key, current) -> {
            if (current != sessionLock) return current;
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    /**
     * Session invalidation must never be blocked by a database outage.
     * In-memory authentication state is authoritative for the connection,
     * while the next successful lifecycle operation can repair persisted status.
     */
    private void markOfflineSafely(int userId) {
        try {
            userDAO.updateStatus(userId, User.Status.OFFLINE);
        } catch (RuntimeException e) {
            logger.warn("Failed to persist offline status for user {} ({}).", userId, e.getClass().getSimpleName());
        }
        try {
            userDAO.updateLastSeen(userId, LocalDateTime.now());
        } catch (RuntimeException e) {
            logger.warn("Failed to persist last-seen timestamp for user {} ({}).", userId, e.getClass().getSimpleName());
        }
    }

    private AuthenticationException invalidSession() {
        return new AuthenticationException("Session is invalid or has expired. Please log in again.");
    }

    private String generateSessionToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String digestToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static final class SessionLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    private record Session(int userId, LocalDateTime expiresAt) {}
    public record LoginResult(User user, String sessionToken, LocalDateTime expiresAt) {}
}
