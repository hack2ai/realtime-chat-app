package com.chatapp.service;

import com.chatapp.database.UserDAO;
import com.chatapp.exception.AuthenticationException;
import com.chatapp.model.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {

    @Test
    void invalidCredentialsUseGenericFailure() {
        UserDAO dao = new InMemoryUserDAO(null);
        AuthenticationService service = new AuthenticationService(dao);

        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login("unknown", "wrong-password"));

        assertEquals("Invalid username/email or password.", error.getMessage());
    }

    @Test
    void oversizedLoginIdentifierIsRejectedBeforeLookup() {
        UserDAO dao = new InMemoryUserDAO(null);
        AuthenticationService service = new AuthenticationService(dao);

        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login("a".repeat(255), "wrong-password"));

        assertEquals("Invalid username/email or password.", error.getMessage());
    }

    @Test
    void oversizedLoginPasswordIsRejectedBeforeLookup() {
        UserDAO dao = new InMemoryUserDAO(null);
        AuthenticationService service = new AuthenticationService(dao);

        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login("alice", "a".repeat(73)));

        assertEquals("Invalid username/email or password.", error.getMessage());
    }

    @Test
    void multibyteLoginPasswordIsBoundByUtf8Bytes() {
        UserDAO dao = new InMemoryUserDAO(null);
        AuthenticationService service = new AuthenticationService(dao);
        String oversizedUtf8Password = "é".repeat(37);

        assertEquals(74, oversizedUtf8Password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login("alice", oversizedUtf8Password));

        assertEquals("Invalid username/email or password.", error.getMessage());
    }

    @Test
    void successfulLoginCreatesExpiringSession() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));

        AuthenticationService.LoginResult result = service.login("alice", "correct-password");

        assertNotNull(result.sessionToken());
        assertFalse(result.sessionToken().isBlank());
        assertEquals(43, result.sessionToken().length(), "32 random bytes should encode to 43 Base64URL characters");
        assertTrue(result.sessionToken().matches("[A-Za-z0-9_-]+"));
        assertEquals(user.getId(), service.validateSession(result.sessionToken()));
        assertNotNull(result.expiresAt());
        assertTrue(result.expiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void sessionStoreContainsOnlyTokenDigests() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));

        AuthenticationService.LoginResult result = service.login("alice", "correct-password");

        Field sessionsField = AuthenticationService.class.getDeclaredField("activeSessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> sessions = (Map<String, ?>) sessionsField.get(service);

        assertEquals(1, sessions.size());
        assertFalse(sessions.containsKey(result.sessionToken()), "Raw bearer tokens must never be stored as session-map keys");
        assertEquals(43, sessions.keySet().iterator().next().length(), "SHA-256 digest should be Base64URL encoded");
    }

    @Test
    void successfulLogoutAllowsAccountToLoginAgain() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));

        AuthenticationService.LoginResult first = service.login("alice", "correct-password");
        service.logout(first.sessionToken());
        AuthenticationService.LoginResult second = service.login("alice", "correct-password");

        assertNotEquals(first.sessionToken(), second.sessionToken());
        assertEquals(user.getId(), service.validateSession(second.sessionToken()));
    }

    @Test
    void duplicateLoginForSameUserIsRejected() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));

        service.login("alice", "correct-password");

        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login("alice", "correct-password"));
        assertEquals("This account is already connected.", error.getMessage());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));

        AuthenticationService.LoginResult result = service.login("alice", "correct-password");
        service.logout(result.sessionToken());

        assertThrows(AuthenticationException.class, () -> service.validateSession(result.sessionToken()));
    }

    @Test
    void logoutStillInvalidatesSessionWhenStatusCleanupFails() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        FailingCleanupUserDAO dao = new FailingCleanupUserDAO(user);
        AuthenticationService service = new AuthenticationService(dao);

        AuthenticationService.LoginResult result = service.login("alice", "correct-password");
        int lastSeenUpdatesBeforeLogout = dao.getLastSeenUpdates();
        dao.setFailStatusUpdate(true);

        assertDoesNotThrow(() -> service.logout(result.sessionToken()));
        assertThrows(AuthenticationException.class, () -> service.validateSession(result.sessionToken()));
        assertTrue(dao.getLastSeenUpdates() > lastSeenUpdatesBeforeLogout,
                "last_seen cleanup must still run when status cleanup fails");
    }

    @Test
    void logoutStillInvalidatesSessionWhenLastSeenCleanupFails() throws Exception {
        User user = userWithHash("alice", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("correct-password"));
        FailingCleanupUserDAO dao = new FailingCleanupUserDAO(user);
        AuthenticationService service = new AuthenticationService(dao);

        AuthenticationService.LoginResult result = service.login("alice", "correct-password");
        int statusUpdatesBeforeLogout = dao.getStatusUpdates();
        dao.setFailLastSeenUpdate(true);

        assertDoesNotThrow(() -> service.logout(result.sessionToken()));
        assertThrows(AuthenticationException.class, () -> service.validateSession(result.sessionToken()));
        assertTrue(dao.getStatusUpdates() > statusUpdatesBeforeLogout,
                "status cleanup must still run when last_seen cleanup fails");
    }

    @Test
    void invalidSessionTokenIsRejected() {
        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(null));

        assertThrows(AuthenticationException.class, () -> service.validateSession("not-a-real-session"));
        assertThrows(AuthenticationException.class, () -> service.validateSession(null));
    }

    private static User userWithHash(String username, String hash) {
        User user = new User(username, username + "@example.com", hash);
        user.setId(42);
        return user;
    }

    private static class InMemoryUserDAO extends UserDAO {
        private final User user;

        private InMemoryUserDAO(User user) {
            this.user = user;
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String identifier) {
            if (user == null) return Optional.empty();
            return user.getUsername().equals(identifier) || user.getEmail().equals(identifier)
                    ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updateStatus(int userId, User.Status status) {
            if (user != null && user.getId() == userId) user.setStatus(status);
        }

        @Override
        public void updateLastSeen(int userId, LocalDateTime lastSeen) {
            if (user != null && user.getId() == userId) user.setLastSeen(lastSeen);
        }
    }

    private static final class FailingCleanupUserDAO extends InMemoryUserDAO {
        private volatile boolean failStatusUpdate;
        private volatile boolean failLastSeenUpdate;
        private int statusUpdates;
        private int lastSeenUpdates;

        private FailingCleanupUserDAO(User user) {
            super(user);
        }

        private void setFailStatusUpdate(boolean value) {
            failStatusUpdate = value;
        }

        private void setFailLastSeenUpdate(boolean value) {
            failLastSeenUpdate = value;
        }

        private synchronized int getStatusUpdates() {
            return statusUpdates;
        }

        private synchronized int getLastSeenUpdates() {
            return lastSeenUpdates;
        }

        @Override
        public synchronized void updateStatus(int userId, User.Status status) {
            if (failStatusUpdate) throw new RuntimeException("simulated database outage");
            statusUpdates++;
            super.updateStatus(userId, status);
        }

        @Override
        public synchronized void updateLastSeen(int userId, LocalDateTime lastSeen) {
            if (failLastSeenUpdate) throw new RuntimeException("simulated database outage");
            lastSeenUpdates++;
            super.updateLastSeen(userId, lastSeen);
        }
    }
}
