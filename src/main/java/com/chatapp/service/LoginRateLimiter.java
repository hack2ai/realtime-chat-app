package com.chatapp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small bounded in-memory limiter for repeated authentication failures. */
public final class LoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        Instant now = Instant.now();
        Attempt current = attempts.get(key);
        if (current == null) return true;
        if (expired(current, now)) {
            attempts.remove(key, current);
            return true;
        }
        return current.failures() < MAX_FAILURES;
    }

    public void recordFailure(String key) {
        if (key == null || key.isBlank()) return;
        Instant now = Instant.now();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || expired(current, now)) {
                enforceCapacity(now);
                return new Attempt(1, now);
            }
            return new Attempt(current.failures() + 1, current.windowStart());
        });
    }

    public void recordSuccess(String key) {
        if (key != null) attempts.remove(key);
    }

    private boolean expired(Attempt attempt, Instant now) {
        return Duration.between(attempt.windowStart(), now).compareTo(WINDOW) >= 0;
    }

    private void enforceCapacity(Instant now) {
        if (attempts.size() < MAX_KEYS) return;
        removeExpired(now);
        if (attempts.size() < MAX_KEYS) return;

        String oldestKey = null;
        Instant oldestStart = now;
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            if (entry.getValue().windowStart().isBefore(oldestStart)) {
                oldestStart = entry.getValue().windowStart();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) attempts.remove(oldestKey);
    }

    private void removeExpired(Instant now) {
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            if (expired(entry.getValue(), now)) attempts.remove(entry.getKey(), entry.getValue());
        }
    }

    private record Attempt(int failures, Instant windowStart) {}
}
