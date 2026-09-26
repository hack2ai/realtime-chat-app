package com.chatapp.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMetricsTest {
    @Test
    void tracksConnectionLifecycleAndRejections() {
        ServerMetrics metrics = new ServerMetrics();

        metrics.connectionAccepted();
        metrics.connectionAccepted();
        metrics.connectionRateLimited();
        metrics.connectionCapacityRejected();
        metrics.outboundQueueRejected();
        metrics.authenticationSucceeded();
        metrics.authenticationFailed();
        metrics.authenticationFailed();
        metrics.protocolError();
        metrics.connectionClosed();
        metrics.connectionClosed();
        metrics.connectionClosed();

        assertEquals(0, metrics.activeConnections());
        assertEquals(2, metrics.peakActiveConnections());
        assertEquals(2, metrics.acceptedConnections());
        assertEquals(1, metrics.rateLimitedConnections());
        assertEquals(1, metrics.capacityRejectedConnections());
        assertEquals(1, metrics.outboundQueueRejectedMessages());
        assertEquals(1, metrics.successfulAuthentications());
        assertEquals(2, metrics.failedAuthentications());
        assertEquals(1, metrics.protocolErrors());
        assertTrue(metrics.uptimeSeconds() >= 0);
        assertTrue(metrics.usedHeapBytes() >= 0);
        assertTrue(metrics.maxHeapBytes() >= metrics.usedHeapBytes());
        assertTrue(metrics.summary().matches(
                "active=0, peakActive=2, acceptedTotal=2, rateLimitedTotal=1, capacityRejectedTotal=1, outboundQueueRejectedTotal=1, authSuccessTotal=1, authFailureTotal=2, protocolErrorTotal=1, uptimeSeconds=\\d+, usedHeapBytes=\\d+, maxHeapBytes=\\d+"));
    }

    @Test
    void remainsConsistentUnderConcurrentUpdates() throws InterruptedException {
        ServerMetrics metrics = new ServerMetrics();
        int workers = 8;
        int incrementsPerWorker = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            for (int worker = 0; worker < workers; worker++) {
                executor.submit(() -> {
                    for (int i = 0; i < incrementsPerWorker; i++) {
                        metrics.connectionAccepted();
                    }
                });
            }
        } finally {
            executor.shutdown();
        }

        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        int totalAccepted = workers * incrementsPerWorker;

        assertEquals(totalAccepted, metrics.acceptedConnections());
        assertEquals(totalAccepted, metrics.activeConnections());
        assertTrue(metrics.peakActiveConnections() > 0);
    }
}
