package com.chatapp.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
        assertThrows(IllegalArgumentException.class,
                () -> new RequestRateLimiter(1, Duration.ofSeconds(Long.MAX_VALUE), 10));
        assertThrows(IllegalArgumentException.class,
                () -> new RequestRateLimiter(1, Duration.ofMinutes(1), 10, null));
    }

    @Test
    void keepsKeyCountBounded() {
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), 3);
        limiter.allow("a");
        limiter.allow("b");
        limiter.allow("c");
        limiter.allow("d");
        assertEquals(3, limiter.size());
    }

    @Test
    void evictsOldestKeyWhenCapacityIsFull() {
        AtomicLong now = new AtomicLong(1_000_000L);
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), 2, now::get);
        assertTrue(limiter.allow("oldest"));
        now.addAndGet(5_000_000L);
        assertTrue(limiter.allow("middle"));
        now.addAndGet(5_000_000L);
        assertTrue(limiter.allow("newest"));

        assertEquals(2, limiter.size());
        assertTrue(limiter.allow("oldest"), "oldest key should be evicted when capacity is full");
        assertFalse(limiter.allow("newest"), "newest key should remain tracked");
    }

    @Test
    void usesMonotonicTimeWhenClockMovesBackward() {
        AtomicLong now = new AtomicLong(1_000_000L);
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofSeconds(60), 10, now::get);

        assertTrue(limiter.allow("user:1"));
        now.set(500_000L);
        assertFalse(limiter.allow("user:1"), "a backwards clock adjustment must not expire an active window");
        now.set(1_000_000L + Duration.ofSeconds(60).toNanos() + 1);
        assertTrue(limiter.allow("user:1"), "window should expire after the full monotonic interval");
    }

    @Test
    void expiresWindowsUsingInjectedMonotonicTime() {
        AtomicLong now = new AtomicLong(1_000_000L);
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofSeconds(25), 10, now::get);

        assertTrue(limiter.allow("short-lived"));
        assertEquals(1, limiter.size());
        now.addAndGet(Duration.ofSeconds(25).toNanos());
        assertEquals(0, limiter.size());
    }

    @Test
    void remainsBoundedUnderConcurrentNewKeys() throws Exception {
        int maxKeys = 25;
        int workers = 16;
        int keysPerWorker = 50;
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), maxKeys);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                final int workerId = worker;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < keysPerWorker; i++) {
                        limiter.allow("worker-" + workerId + "-" + i);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertTrue(limiter.size() <= maxKeys,
                    "limiter key count exceeded configured bound");
        } finally {
            executor.shutdownNow();
        }
    }
}
