package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
