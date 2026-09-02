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
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Handles registration, login, and server-side session lifecycle. */
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String GENERIC_LOGIN_FAILURE = "Invalid username/email or password.";
    // Structurally valid bcrypt hash used only to keep unknown-user login timing
    // comparable with known-user password verification. It is not an application credential.
    private static final String DUMMY_BCRYPT_HASH =
            "$2b$12$DZ6D.G/D/VQG3SKNjuFZPeKNHCMKre5204h62M30unldZi/Zk/nKm";

    private final UserDAO userDAO;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /** token -> session */
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    /** user id -> token; prevents multiple active sessions for one account */
    private final Map<Integer, String> activeTokenByUser = new ConcurrentHashMap<>();

    public AuthenticationService() {
        this.userDAO = new UserDAO();
        this.passwordEncoder = new BCryptPasswordEncoder(AppConfig.getBcryptStrength());
    }

    /** Constructor for unit tests that inject a DAO implementation. */
    public AuthenticationService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.passwordEncoder = new BCryptPasswordEncoder(AppConfig.getBcryptStrength());
    }

    public User register(String username, String email, String password, String confirmPassword)
            throws ValidationException {
        ValidationUtil.validateUsername(username);
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePassword(password);
        ValidationUtil.validatePasswordsMatch(password, confirmPassword);

        // BCrypt implementations use only the first 72 UTF-8 bytes.
        // Rejecting longer values avoids silently hashing a different password
        // from the one the user believes they selected.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationException("Password must not exceed 72 UTF-8 bytes.");
        }
        if (userDAO.usernameExists(username)) {
            throw new ValidationException("Username '" + username + "' is already taken.");
        }
        if (userDAO.emailExists(email)) {
            throw new ValidationException("An account with this email already exists.");
        }

        User saved = userDAO.insert(new User(username, email, passwordEncoder.encode(password)));
        logger.info("New user registered: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    public LoginResult login(String usernameOrEmail, String password) throws AuthenticationException {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationException(GENERIC_LOGIN_FAILURE);
        }

        User user = userDAO.findByUsernameOrEmail(usernameOrEmail).orElse(null);
        String hashToCheck = user == null ? DUMMY_BCRYPT_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);

        if (user == null || !passwordMatches) {
            logger.info("Failed login attempt for identifier: {}", usernameOrEmail);
            throw new AuthenticationException(GENERIC_LOGIN_FAILURE);
        }

        String token = generateSessionToken();
        String existingToken = activeTokenByUser.putIfAbsent(user.getId(), token);
        if (existingToken != null) {
            throw new AuthenticationException("This account is already connected.");
        }

        LocalDateTime expiry = LocalDateTime.now().plusHours(AppConfig.getSessionExpiryHours());
        activeSessions.put(token, new Session(user.getId(), expiry));
        userDAO.updateStatus(user.getId(), User.Status.ONLINE);
        logger.info("User logged in: id={}, username={}", user.getId(), user.getUsername());

        return new LoginResult(user, token);
    }

    public void logout(String sessionToken) {
        if (sessionToken == null) {
            return;
        }
        Session session = activeSessions.remove(sessionToken);
        if (session != null) {
            activeTokenByUser.remove(session.userId, sessionToken);
            userDAO.updateStatus(session.userId, User.Status.OFFLINE);
            userDAO.updateLastSeen(session.userId, LocalDateTime.now());
            logger.info("User logged out: userId={}", session.userId);
        }
    }

    public int validateSession(String sessionToken) throws AuthenticationException {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new AuthenticationException("Session is invalid or has expired. Please log in again.");
        }
        Session session = activeSessions.get(sessionToken);
        if (session == null) {
            throw new AuthenticationException("Session is invalid or has expired. Please log in again.");
        }
        if (LocalDateTime.now().isAfter(session.expiresAt)) {
            if (activeSessions.remove(sessionToken, session)) {
                activeTokenByUser.remove(session.userId, sessionToken);
                userDAO.updateStatus(session.userId, User.Status.OFFLINE);
                userDAO.updateLastSeen(session.userId, LocalDateTime.now());
            }
            throw new AuthenticationException("Session is invalid or has expired. Please log in again.");
        }
        return session.userId;
    }

    private String generateSessionToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private record Session(int userId, LocalDateTime expiresAt) {
    }

    public record LoginResult(User user, String sessionToken) {
    }
}
