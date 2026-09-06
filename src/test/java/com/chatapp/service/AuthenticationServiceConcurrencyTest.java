package com.chatapp.service;

import com.chatapp.database.UserDAO;
import com.chatapp.exception.AuthenticationException;
import com.chatapp.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceConcurrencyTest {

    @Test
    void concurrentLoginsAllowExactlyOneActiveSession() throws Exception {
        User user = new User(
                "alice",
                "alice@example.com",
                new BCryptPasswordEncoder(10).encode("correct-password"));
        user.setId(42);

        CyclicBarrier simultaneousLookup = new CyclicBarrier(2);
        AuthenticationService service = new AuthenticationService(
                new ConcurrentLoginUserDAO(user, simultaneousLookup));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<AuthenticationService.LoginResult>> results = executor.invokeAll(List.of(
                    () -> service.login("alice", "correct-password"),
                    () -> service.login("alice", "correct-password")
            ));

            int successfulLogins = 0;
            int rejectedLogins = 0;
            for (java.util.concurrent.Future<AuthenticationService.LoginResult> result : results) {
                try {
                    assertTrue(result.get(5, TimeUnit.SECONDS).sessionToken().length() == 43);
                    successfulLogins++;
                } catch (java.util.concurrent.ExecutionException error) {
                    Throwable cause = error.getCause();
                    assertTrue(cause instanceof AuthenticationException);
                    assertEquals("This account is already connected.", cause.getMessage());
                    rejectedLogins++;
                }
            }

            assertEquals(1, successfulLogins);
            assertEquals(1, rejectedLogins);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class ConcurrentLoginUserDAO extends UserDAO {
        private final User user;
        private final CyclicBarrier simultaneousLookup;

        private ConcurrentLoginUserDAO(User user, CyclicBarrier simultaneousLookup) {
            this.user = user;
            this.simultaneousLookup = simultaneousLookup;
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String identifier) {
            try {
                simultaneousLookup.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Concurrent login test could not synchronize lookups.", e);
            }
            if (user.getUsername().equals(identifier) || user.getEmail().equals(identifier)) {
                return Optional.of(user);
            }
            return Optional.empty();
        }

        @Override
        public void updateStatus(int userId, User.Status status) {
            if (user.getId() == userId) {
                user.setStatus(status);
            }
        }

        @Override
        public void updateLastSeen(int userId, LocalDateTime lastSeen) {
            if (user.getId() == userId) {
                user.setLastSeen(lastSeen);
            }
        }
    }
}
