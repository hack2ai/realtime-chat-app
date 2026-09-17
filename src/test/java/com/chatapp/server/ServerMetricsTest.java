package com.chatapp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerMetricsTest {
    @Test
    void tracksConnectionLifecycleAndRejections() {
        ServerMetrics metrics = new ServerMetrics();

        metrics.connectionAccepted();
        metrics.connectionAccepted();
        metrics.connectionRateLimited();
        metrics.connectionCapacityRejected();
        metrics.connectionClosed();
        metrics.connectionClosed();
        metrics.connectionClosed();

        assertEquals(0, metrics.activeConnections());
        assertEquals(2, metrics.acceptedConnections());
        assertEquals(1, metrics.rateLimitedConnections());
        assertEquals(1, metrics.capacityRejectedConnections());
        assertEquals(
                "active=0, acceptedTotal=2, rateLimitedTotal=1, capacityRejectedTotal=1",
                metrics.summary());
    }
}
