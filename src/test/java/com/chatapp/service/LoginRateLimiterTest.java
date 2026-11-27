package com.chatapp.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    void remainsBoundedUnderConcurrentNewKeys() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter();
        int workers = 16;
        int keysPerWorker = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workers; worker++) {
                final int workerId = worker;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < keysPerWorker; i++) {
                        limiter.recordFailure("worker-" + workerId + "-" + i);
                    }
                    return null;
                }));
            }

            start.countDown();
            for (var future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            Field attemptsField = LoginRateLimiter.class.getDeclaredField("attempts");
            attemptsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ?> attempts = (Map<String, ?>) attemptsField.get(limiter);

            assertEquals(10_000, attempts.size(),
                    "limiter state must stay within its configured maximum key bound");
        } finally {
            executor.shutdownNow();
        }
    }
}
