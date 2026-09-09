package com.chatapp.server;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe operational counters for the running chat server. */
public final class ServerMetrics {
    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong protocolErrors = new AtomicLong();
    private final AtomicLong authenticationFailures = new AtomicLong();
    private final AtomicLong rateLimitedRequests = new AtomicLong();
    private final AtomicLong internalErrors = new AtomicLong();

    public void recordAcceptedConnection() {
        acceptedConnections.incrementAndGet();
    }

    public void recordRejectedConnection() {
        rejectedConnections.incrementAndGet();
    }

    public void recordRequest() {
        requests.incrementAndGet();
    }

    public void recordProtocolError() {
        protocolErrors.incrementAndGet();
    }

    public void recordAuthenticationFailure() {
        authenticationFailures.incrementAndGet();
    }

    public void recordRateLimitedRequest() {
        rateLimitedRequests.incrementAndGet();
    }

    public void recordInternalError() {
        internalErrors.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                acceptedConnections.get(),
                rejectedConnections.get(),
                requests.get(),
                protocolErrors.get(),
                authenticationFailures.get(),
                rateLimitedRequests.get(),
                internalErrors.get());
    }

    public record Snapshot(
            long acceptedConnections,
            long rejectedConnections,
            long requests,
            long protocolErrors,
            long authenticationFailures,
            long rateLimitedRequests,
            long internalErrors) {
    }
}
