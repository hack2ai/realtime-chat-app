package com.chatapp.service;

import com.chatapp.database.UserDAO;
import com.chatapp.exception.AuthenticationException;
import com.chatapp.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationSessionExpiryTest {

    @Test
    void expiredSessionDoesNotBlockSubsequentLogin() throws Exception {
        String password = "correct-password";
        User user = new User("alice", "alice@example.com",
                new BCryptPasswordEncoder(10).encode(password));
        user.setId(42);

        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));
        AuthenticationService.LoginResult first = service.login("alice", password);

        Field sessionsField = AuthenticationService.class.getDeclaredField("activeSessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> sessions = (Map<String, Object>) sessionsField.get(service);
        String digest = sessions.keySet().iterator().next();

        Class<?> sessionClass = Class.forName("com.chatapp.service.AuthenticationService$Session");
        Constructor<?> constructor = sessionClass.getDeclaredConstructor(int.class, LocalDateTime.class);
        constructor.setAccessible(true);
        sessions.put(digest, constructor.newInstance(user.getId(), LocalDateTime.now().minusMinutes(1)));

        AuthenticationService.LoginResult second = service.login("alice", password);

        assertNotEquals(first.sessionToken(), second.sessionToken());
        assertThrows(AuthenticationException.class, () -> service.validateSession(first.sessionToken()));
        assertEquals(user.getId(), service.validateSession(second.sessionToken()));
    }

    @Test
    void cleanupExpiredSessionsRemovesExpiredSessionAndMarksUserOffline() throws Exception {
        String password = "correct-password";
        User user = new User("cleanup", "cleanup@example.com",
                new BCryptPasswordEncoder(10).encode(password));
        user.setId(44);

        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));
        AuthenticationService.LoginResult result = service.login("cleanup", password);
        assertEquals(User.Status.ONLINE, user.getStatus());

        Field sessionsField = AuthenticationService.class.getDeclaredField("activeSessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> sessions = (Map<String, Object>) sessionsField.get(service);
        String digest = sessions.keySet().iterator().next();

        Class<?> sessionClass = Class.forName("com.chatapp.service.AuthenticationService$Session");
        Constructor<?> constructor = sessionClass.getDeclaredConstructor(int.class, LocalDateTime.class);
        constructor.setAccessible(true);
        sessions.put(digest, constructor.newInstance(user.getId(), LocalDateTime.now().minusMinutes(1)));

        assertEquals(1, service.cleanupExpiredSessions());
        assertEquals(User.Status.OFFLINE, user.getStatus());
        assertThrows(AuthenticationException.class, () -> service.validateSession(result.sessionToken()));
        assertEquals(0, service.cleanupExpiredSessions());
    }

    @Test
    void sessionTokenUsesExpectedBase64UrlLengthAndRejectsOversizedTokens() throws Exception {
        String password = "correct-password";
        User user = new User("bob", "bob@example.com",
                new BCryptPasswordEncoder(10).encode(password));
        user.setId(43);

        AuthenticationService service = new AuthenticationService(new InMemoryUserDAO(user));
        AuthenticationService.LoginResult result = service.login("bob", password);

        assertEquals(43, result.sessionToken().length());
        assertTrue(result.sessionToken().matches("[A-Za-z0-9_-]+"));
        assertEquals(user.getId(), service.validateSession(result.sessionToken()));
        assertThrows(AuthenticationException.class, () -> service.validateSession("x".repeat(65)));

        service.logout(result.sessionToken());
        assertThrows(AuthenticationException.class, () -> service.validateSession(result.sessionToken()));
    }

    private static final class InMemoryUserDAO extends UserDAO {
        private final User user;

        private InMemoryUserDAO(User user) {
            this.user = user;
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String identifier) {
            return user.getUsername().equals(identifier) || user.getEmail().equals(identifier)
                    ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updateStatus(int userId, User.Status status) {
            if (user.getId() == userId) user.setStatus(status);
        }

        @Override
        public void updateLastSeen(int userId, LocalDateTime lastSeen) {
            if (user.getId() == userId) user.setLastSeen(lastSeen);
        }
    }
}
