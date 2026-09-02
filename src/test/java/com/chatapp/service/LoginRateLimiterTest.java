package com.chatapp.service;

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
}
