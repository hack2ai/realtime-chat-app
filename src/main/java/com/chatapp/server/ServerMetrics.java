package com.chatapp.server;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight, dependency-free server metrics for operational logs. */
public final class ServerMetrics {
    private final long startedAtNanos = System.nanoTime();
    private final AtomicLong activeConnections = new AtomicLong();
    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rateLimitedConnections = new AtomicLong();
    private final AtomicLong capacityRejectedConnections = new AtomicLong();

    public void connectionAccepted() {
        acceptedConnections.incrementAndGet();
        activeConnections.incrementAndGet();
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

    public long activeConnections() {
        return activeConnections.get();
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
                + ", acceptedTotal=" + acceptedConnections()
                + ", rateLimitedTotal=" + rateLimitedConnections()
                + ", capacityRejectedTotal=" + capacityRejectedConnections()
                + ", uptimeSeconds=" + uptimeSeconds()
                + ", usedHeapBytes=" + usedHeapBytes()
                + ", maxHeapBytes=" + maxHeapBytes();
    }
}
