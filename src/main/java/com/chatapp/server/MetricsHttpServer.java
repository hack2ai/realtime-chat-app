package com.chatapp.server;

import com.chatapp.config.AppConfig;
import com.chatapp.service.RequestRateLimiter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional read-only HTTP endpoint for Prometheus-style operational metrics. */
public final class MetricsHttpServer {
    private static final RequestRateLimiter METRICS_RATE_LIMITER =
            new RequestRateLimiter(60, java.time.Duration.ofMinutes(1), 10_000);

    private final ServerMetrics metrics;
    private final ChatServer server;
    private HttpServer httpServer;
    private ExecutorService executor;

    public MetricsHttpServer(ServerMetrics metrics, ChatServer server) {
        this.metrics = metrics;
        this.server = server;
    }

    public synchronized void start() throws IOException {
        if (httpServer != null) return;
        if (!AppConfig.isMetricsEnabled()) return;

        InetAddress address = InetAddress.getByName(AppConfig.getMetricsBindAddress());
        HttpServer candidate = HttpServer.create(new InetSocketAddress(address, AppConfig.getMetricsPort()), 0);
        ExecutorService candidateExecutor = Executors.newVirtualThreadPerTaskExecutor();
        candidate.createContext("/metrics", this::handleMetrics);
        candidate.setExecutor(candidateExecutor);
        try {
            candidate.start();
        } catch (RuntimeException | Error e) {
            candidateExecutor.shutdownNow();
            throw e;
        }
        httpServer = candidate;
        executor = candidateExecutor;
    }

    public synchronized void stop() {
        if (httpServer == null) return;
        httpServer.stop(0);
        httpServer = null;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String key = exchange.getRemoteAddress().getAddress() == null
                    ? "metrics:unknown"
                    : "metrics:" + exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!METRICS_RATE_LIMITER.allow(key)) {
                exchange.getResponseHeaders().set("Retry-After", "60");
                exchange.sendResponseHeaders(429, -1);
                return;
            }

            byte[] payload = renderMetrics().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
        }
    }

    private String renderMetrics() {
        ServerMetrics.Snapshot snapshot = metrics.snapshot();
        Runtime runtime = Runtime.getRuntime();
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long liveThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        StringBuilder output = new StringBuilder(3200);
        appendGauge(output, "chatapp_connected_users", "Currently connected authenticated users.", server.connectedUserCount());
        appendGauge(output, "chatapp_active_handlers", "Currently active client handlers.", server.activeHandlerCount());
        appendCounter(output, "chatapp_accepted_connections_total", "Accepted client connections.", snapshot.acceptedConnections());
        appendCounter(output, "chatapp_rejected_connections_total", "Rejected client connections.", snapshot.rejectedConnections());
        appendCounter(output, "chatapp_requests_total", "Processed client requests.", snapshot.requests());
        appendCounter(output, "chatapp_protocol_errors_total", "Protocol errors observed.", snapshot.protocolErrors());
        appendCounter(output, "chatapp_authentication_failures_total", "Authentication failures observed.", snapshot.authenticationFailures());
        appendCounter(output, "chatapp_rate_limited_requests_total", "Requests rejected by application rate limits.", snapshot.rateLimitedRequests());
        appendGauge(output, "chatapp_handler_pool_active", "Active client handler executor tasks.", server.handlerPoolActiveCount());
        appendGauge(output, "chatapp_handler_pool_size", "Current client handler executor pool size.", server.handlerPoolSize());
        appendGauge(output, "chatapp_handler_pool_queue_depth", "Queued client handler tasks.", server.handlerPoolQueueDepth());
        appendCounter(output, "chatapp_handler_pool_completed_total", "Completed client handler tasks.", server.completedHandlerCount());
        appendGauge(output, "chatapp_jvm_memory_used_bytes", "JVM heap memory currently used.", runtime.totalMemory() - runtime.freeMemory());
        appendGauge(output, "chatapp_jvm_memory_committed_bytes", "JVM heap memory currently committed.", runtime.totalMemory());
        appendGauge(output, "chatapp_jvm_memory_max_bytes", "Maximum JVM heap memory available.", runtime.maxMemory());
        appendGauge(output, "chatapp_jvm_uptime_seconds", "JVM uptime in seconds.", uptimeMillis / 1000);
        appendGauge(output, "chatapp_jvm_threads_live", "Currently live JVM threads.", liveThreads);
        return output.toString();
    }

    private static void appendCounter(StringBuilder output, String name, String help, long value) {
        output.append("# HELP ").append(name).append(' ').append(help).append('\n');
        output.append("# TYPE ").append(name).append(" counter\n");
        output.append(name).append(' ').append(value).append('\n');
    }

    private static void appendGauge(StringBuilder output, String name, String help, long value) {
        output.append("# HELP ").append(name).append(' ').append(help).append('\n');
        output.append("# TYPE ").append(name).append(" gauge\n");
        output.append(name).append(' ').append(value).append('\n');
    }
}
