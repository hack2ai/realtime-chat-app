package com.chatapp.server;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight, dependency-free server metrics for operational logs. */
public final class ServerMetrics {
    private final long startedAtNanos = System.nanoTime();
    private final AtomicLong activeConnections = new AtomicLong();
    private final AtomicLong peakActiveConnections = new AtomicLong();
    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rateLimitedConnections = new AtomicLong();
    private final AtomicLong capacityRejectedConnections = new AtomicLong();
    private final AtomicLong successfulAuthentications = new AtomicLong();
    private final AtomicLong failedAuthentications = new AtomicLong();
    private final AtomicLong protocolErrors = new AtomicLong();

    public void connectionAccepted() {
        acceptedConnections.incrementAndGet();
        long active = activeConnections.incrementAndGet();
        peakActiveConnections.updateAndGet(current -> Math.max(current, active));
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(value -> value > 0 ? value - 1 : 0);
    }

    public void connectionRateLimited() {
        rateLimitedConnections.incrementAndGet();
    }

    public void connectionCapacityRejected() {
        capacityRejectedConnections.incrementAndGet();
    }

    public void authenticationSucceeded() {
        successfulAuthentications.incrementAndGet();
    }

    public void authenticationFailed() {
        failedAuthentications.incrementAndGet();
    }

    public void protocolError() {
        protocolErrors.incrementAndGet();
    }

    public long activeConnections() {
        return activeConnections.get();
    }

    public long peakActiveConnections() {
        return peakActiveConnections.get();
    }

    public long acceptedConnections() {
        return acceptedConnections.get();
    }

    public long rateLimitedConnections() {
        return rateLimitedConnections.get();
    }

    public long capacityRejectedConnections() {
        return capacityRejectedConnections.get();
    }

    public long successfulAuthentications() {
        return successfulAuthentications.get();
    }

    public long failedAuthentications() {
        return failedAuthentications.get();
    }

    public long protocolErrors() {
        return protocolErrors.get();
    }

    public long uptimeSeconds() {
        return TimeUnit.NANOSECONDS.toSeconds(Math.max(0L, System.nanoTime() - startedAtNanos));
    }

    public long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    public long maxHeapBytes() {
        return Math.max(0L, Runtime.getRuntime().maxMemory());
    }

    public String summary() {
        return "active=" + activeConnections()
                + ", peakActive=" + peakActiveConnections()
                + ", acceptedTotal=" + acceptedConnections()
                + ", rateLimitedTotal=" + rateLimitedConnections()
                + ", capacityRejectedTotal=" + capacityRejectedConnections()
                + ", authSuccessTotal=" + successfulAuthentications()
                + ", authFailureTotal=" + failedAuthentications()
                + ", protocolErrorTotal=" + protocolErrors()
                + ", uptimeSeconds=" + uptimeSeconds()
                + ", usedHeapBytes=" + usedHeapBytes()
                + ", maxHeapBytes=" + maxHeapBytes();
    }
}
