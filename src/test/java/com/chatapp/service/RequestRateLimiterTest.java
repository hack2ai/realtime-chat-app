package com.chatapp.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
