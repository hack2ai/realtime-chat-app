package com.chatapp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded fixed-window limiter for authenticated protocol requests. */
public final class RequestRateLimiter {
    private final int maxRequests;
    private final Duration window;
    private final int maxKeys;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RequestRateLimiter(int maxRequests, Duration window, int maxKeys) {
        if (maxRequests <= 0 || window == null || window.isZero() || window.isNegative() || maxKeys <= 0) {
            throw new IllegalArgumentException("Invalid rate limiter configuration");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.maxKeys = maxKeys;
    }

    /**
     * Atomically admits a request and enforces the global key bound.
     * Synchronization is intentionally coarse-grained: admission is a tiny in-memory
     * operation, and correctness of the memory bound is more important than parallelism here.
     */
    public synchronized boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        Instant now = Instant.now();
        Window current = windows.get(key);
        if (current == null || expired(current, now)) {
            enforceCapacity(now);
            windows.put(key, new Window(1, now));
            return true;
        }
        if (current.requests() >= maxRequests) return false;
        windows.put(key, new Window(current.requests() + 1, current.windowStart()));
        return true;
    }

    public int size() { return windows.size(); }

    private boolean expired(Window value, Instant now) {
        return Duration.between(value.windowStart(), now).compareTo(window) >= 0;
    }

    private void enforceCapacity(Instant now) {
        if (windows.size() < maxKeys) return;
        removeExpired(now);
        if (windows.size() < maxKeys) return;
        String oldestKey = null;
        Instant oldest = now;
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            if (entry.getValue().windowStart().isBefore(oldest)) {
                oldest = entry.getValue().windowStart();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) windows.remove(oldestKey);
    }

    private void removeExpired(Instant now) {
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            if (expired(entry.getValue(), now)) windows.remove(entry.getKey(), entry.getValue());
        }
    }

    private record Window(int requests, Instant windowStart) {}
}
