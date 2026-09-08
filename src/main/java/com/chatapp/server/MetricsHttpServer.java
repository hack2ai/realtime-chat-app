package com.chatapp.server;

import com.chatapp.config.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Optional read-only HTTP endpoint for Prometheus-style operational metrics. */
public final class MetricsHttpServer {
    private final ServerMetrics metrics;
    private final ChatServer server;
    private HttpServer httpServer;

    public MetricsHttpServer(ServerMetrics metrics, ChatServer server) {
        this.metrics = metrics;
        this.server = server;
    }

    public synchronized void start() throws IOException {
        if (httpServer != null) return;
        if (!AppConfig.isMetricsEnabled()) return;

        InetAddress address = InetAddress.getByName(AppConfig.getMetricsBindAddress());
        httpServer = HttpServer.create(new java.net.InetSocketAddress(address, AppConfig.getMetricsPort()), 0);
        httpServer.createContext("/metrics", this::handleMetrics);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        httpServer.start();
    }

    public synchronized void stop() {
        if (httpServer == null) return;
        httpServer.stop(0);
        httpServer = null;
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
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
        StringBuilder output = new StringBuilder(2048);
        appendCounter(output, "chatapp_connected_users", "Currently connected authenticated users.", server.connectedUserCount());
        appendCounter(output, "chatapp_active_handlers", "Currently active client handlers.", server.activeHandlerCount());
        appendCounter(output, "chatapp_accepted_connections_total", "Accepted client connections.", snapshot.acceptedConnections());
        appendCounter(output, "chatapp_rejected_connections_total", "Rejected client connections.", snapshot.rejectedConnections());
        appendCounter(output, "chatapp_requests_total", "Processed client requests.", snapshot.requests());
        appendCounter(output, "chatapp_protocol_errors_total", "Protocol errors observed.", snapshot.protocolErrors());
        appendCounter(output, "chatapp_handler_pool_active", "Active client handler executor tasks.", server.handlerPoolActiveCount());
        appendCounter(output, "chatapp_handler_pool_size", "Current client handler executor pool size.", server.handlerPoolSize());
        appendCounter(output, "chatapp_handler_pool_queue_depth", "Queued client handler tasks.", server.handlerPoolQueueDepth());
        appendCounter(output, "chatapp_handler_pool_completed_total", "Completed client handler tasks.", server.completedHandlerCount());
        appendGauge(output, "chatapp_jvm_memory_used_bytes", "JVM heap memory currently used.", runtime.totalMemory() - runtime.freeMemory());
        appendGauge(output, "chatapp_jvm_memory_committed_bytes", "JVM heap memory currently committed.", runtime.totalMemory());
        appendGauge(output, "chatapp_jvm_memory_max_bytes", "Maximum JVM heap memory available.", runtime.maxMemory());
        return output.toString();
    }

    private static void appendCounter(StringBuilder output, String name, String help, long value) {
        output.append("# HELP ").append(name).append(' ').append(help).append('\n');
        output.append("# TYPE ").append(name).append(" gauge\n");
        output.append(name).append(' ').append(value).append('\n');
    }

    private static void appendGauge(StringBuilder output, String name, String help, long value) {
        output.append("# HELP ").append(name).append(' ').append(help).append('\n');
        output.append("# TYPE ").append(name).append(" gauge\n");
        output.append(name).append(' ').append(value).append('\n');
    }
}
