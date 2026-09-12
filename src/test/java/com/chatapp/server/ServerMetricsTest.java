package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ServerMetricsTest {

    @Test
    void snapshotTracksOperationalCounters() {
        ServerMetrics metrics = new ServerMetrics();

        metrics.recordAcceptedConnection();
        metrics.recordRejectedConnection();
        metrics.recordRequest();
        metrics.recordRequest();
        metrics.recordProtocolError();

        assertEquals(new ServerMetrics.Snapshot(1, 1, 2, 1), metrics.snapshot());
    }

    @Test
    void freshMetricsStartAtZero() {
        ServerMetrics metrics = new ServerMetrics();

        assertEquals(new ServerMetrics.Snapshot(0, 0, 0, 0), metrics.snapshot());
    }

    @Test
    void countersRemainAccurateUnderConcurrentUpdates() throws Exception {
        ServerMetrics metrics = new ServerMetrics();
        int workers = 8;
        int iterations = 1_000;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        metrics.recordAcceptedConnection();
                        metrics.recordRejectedConnection();
                        metrics.recordRequest();
                        metrics.recordProtocolError();
                    }
                    return null;
                });
            }
            start.countDown();
        }

        long expected = (long) workers * iterations;
        assertEquals(new ServerMetrics.Snapshot(expected, expected, expected, expected), metrics.snapshot());
    }
}
