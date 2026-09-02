package com.chatapp.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {
    @Test
    void blocksAfterConfiguredLimit() {
        RequestRateLimiter limiter = new RequestRateLimiter(2, Duration.ofMinutes(1), 10);
        assertTrue(limiter.allow("user:1"));
        assertTrue(limiter.allow("user:1"));
        assertFalse(limiter.allow("user:1"));
    }

    @Test
    void limitsAreIndependentPerKey() {
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), 10);
        assertTrue(limiter.allow("user:1"));
        assertFalse(limiter.allow("user:1"));
        assertTrue(limiter.allow("user:2"));
    }

    @Test
    void rejectsInvalidKeys() {
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), 10);
        assertFalse(limiter.allow(null));
        assertFalse(limiter.allow(" "));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RequestRateLimiter(0, Duration.ofMinutes(1), 10));
        assertThrows(IllegalArgumentException.class, () -> new RequestRateLimiter(1, Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> new RequestRateLimiter(1, Duration.ofMinutes(1), 0));
    }
}
