package com.chatapp.service;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    @Test
    void allowsFiveFailuresButBlocksTheSixth() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "127.0.0.1|alice";

        assertTrue(limiter.allow(key));
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }

        assertFalse(limiter.allow(key));
    }

    @Test
    void successClearsFailureState() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "127.0.0.1|alice";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        assertFalse(limiter.allow(key));

        limiter.recordSuccess(key);
        assertTrue(limiter.allow(key));
    }

    @Test
    void blankKeysAreRejected() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        assertFalse(limiter.allow(null));
        assertFalse(limiter.allow("   "));
    }

    @Test
    void rejectsNullClock() {
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(null));
    }

    @Test
    void usesMonotonicTimeWhenClockMovesBackward() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LoginRateLimiter limiter = new LoginRateLimiter(now::get);
        String key = "127.0.0.1|alice";

        assertTrue(limiter.allow(key));
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        assertFalse(limiter.allow(key));

        now.set(500_000L);
        assertFalse(limiter.allow(key), "a backwards clock adjustment must not expire an active login window");

        now.set(1_000_000L + Duration.ofMinutes(1).toNanos() + 1);
        assertTrue(limiter.allow(key), "window should expire after the full monotonic interval");
    }
}
