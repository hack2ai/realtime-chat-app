package com.chatapp.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Small bounded in-memory limiter for repeated authentication failures. */
public final class LoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long WINDOW_NANOS = WINDOW.toNanos();

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public LoginRateLimiter() {
        this(System::nanoTime);
    }

    LoginRateLimiter(LongSupplier nanoTime) {
        if (nanoTime == null) {
            throw new IllegalArgumentException("Invalid login rate limiter clock");
        }
        this.nanoTime = nanoTime;
    }

    public synchronized boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        long now = nanoTime.getAsLong();
        Attempt current = attempts.get(key);
        if (current == null) return true;
        if (expired(current, now)) {
            attempts.remove(key, current);
            return true;
        }
        return current.failures() < MAX_FAILURES;
    }

    public synchronized void recordFailure(String key) {
        if (key == null || key.isBlank()) return;
        long now = nanoTime.getAsLong();
        Attempt current = attempts.get(key);
        if (current == null || expired(current, now)) {
            enforceCapacity(now);
            attempts.put(key, new Attempt(1, now));
            return;
        }
        attempts.put(key, new Attempt(current.failures() + 1, current.windowStartNanos()));
    }

    public synchronized void recordSuccess(String key) {
        if (key != null) attempts.remove(key);
    }

    private boolean expired(Attempt attempt, long now) {
        return now - attempt.windowStartNanos() >= WINDOW_NANOS;
    }

    private void enforceCapacity(long now) {
        if (attempts.size() < MAX_KEYS) return;
        removeExpired(now);
        if (attempts.size() < MAX_KEYS) return;

        String oldestKey = null;
        long oldestStart = now;
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            if (entry.getValue().windowStartNanos() < oldestStart) {
                oldestStart = entry.getValue().windowStartNanos();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) attempts.remove(oldestKey);
    }

    private void removeExpired(long now) {
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            if (expired(entry.getValue(), now)) attempts.remove(entry.getKey(), entry.getValue());
        }
    }

    private record Attempt(int failures, long windowStartNanos) {}
}
