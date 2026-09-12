package com.chatapp.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Bounded fixed-window limiter for authenticated protocol requests. */
public final class RequestRateLimiter {
    private final int maxRequests;
    private final Duration window;
    private final long windowNanos;
    private final int maxKeys;
    private final LongSupplier nanoTime;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RequestRateLimiter(int maxRequests, Duration window, int maxKeys) {
        this(maxRequests, window, maxKeys, System::nanoTime);
    }

    RequestRateLimiter(int maxRequests, Duration window, int maxKeys, LongSupplier nanoTime) {
        if (maxRequests <= 0 || window == null || window.isZero() || window.isNegative()
                || maxKeys <= 0 || nanoTime == null) {
            throw new IllegalArgumentException("Invalid rate limiter configuration");
        }
        try {
            this.windowNanos = window.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Rate limiter window is too large", e);
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.maxKeys = maxKeys;
        this.nanoTime = nanoTime;
    }

    /** Atomically admits a request while keeping limiter state bounded. */
    public synchronized boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        long now = nanoTime.getAsLong();
        Window current = windows.get(key);
        if (current == null || expired(current, now)) {
            enforceCapacity(now);
            windows.put(key, new Window(1, now));
            return true;
        }
        if (current.requests() >= maxRequests) return false;
        windows.put(key, new Window(current.requests() + 1, current.windowStartNanos()));
        return true;
    }

    /** Returns the current number of tracked limiter keys and removes expired entries. */
    public synchronized int size() {
        removeExpired(nanoTime.getAsLong());
        return windows.size();
    }

    private boolean expired(Window value, long now) {
        return now - value.windowStartNanos() >= windowNanos;
    }

    private void enforceCapacity(long now) {
        if (windows.size() < maxKeys) return;
        removeExpired(now);
        if (windows.size() < maxKeys) return;
        String oldestKey = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            if (entry.getValue().windowStartNanos() < oldest) {
                oldest = entry.getValue().windowStartNanos();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) windows.remove(oldestKey);
    }

    private void removeExpired(long now) {
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            if (expired(entry.getValue(), now)) windows.remove(entry.getKey(), entry.getValue());
        }
    }

    private record Window(int requests, long windowStartNanos) {}
}
