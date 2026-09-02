package com.chatapp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory limiter for repeated authentication failures. */
public final class LoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        Instant now = Instant.now();
        Attempt current = attempts.get(key);
        if (current == null) return true;
        if (Duration.between(current.windowStart(), now).compareTo(WINDOW) >= 0) {
            attempts.remove(key, current);
            return true;
        }
        return current.failures() < MAX_FAILURES;
    }

    public void recordFailure(String key) {
        if (key == null || key.isBlank()) return;
        Instant now = Instant.now();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || Duration.between(current.windowStart(), now).compareTo(WINDOW) >= 0) {
                return new Attempt(1, now);
            }
            return new Attempt(current.failures() + 1, current.windowStart());
        });
    }

    public void recordSuccess(String key) {
        if (key != null) attempts.remove(key);
    }

    private record Attempt(int failures, Instant windowStart) {}
}
