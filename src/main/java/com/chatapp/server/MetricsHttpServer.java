package com.chatapp.server;

import com.chatapp.database.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight Prometheus-style metrics HTTP endpoint. */
public final class MetricsHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsHttpServer.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ServerMetrics metrics;
    private final ChatServer server;
    private final com.sun.net.httpserver.HttpServer httpServer;
    private final ExecutorService executor;

    public MetricsHttpServer(ServerMetrics metrics, ChatServer server) throws IOException {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.server = Objects.requireNonNull(server, "server");
        this.httpServer = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress(com.chatapp.config.AppConfig.getMetricsBindAddress(), com.chatapp.config.AppConfig.getMetricsPort()),
                0);
        this.httpServer.createContext("/metrics", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            String token = com.chatapp.config.AppConfig.getMetricsAuthToken();
            if (com.chatapp.config.AppConfig.isMetricsRemoteAllowed()
                    && !isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"), token)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = renderMetrics().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.httpServer.setExecutor(executor);
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(0);
        executor.close();
    }

    static boolean isRemoteExposureAllowed(boolean remoteExposure, boolean remoteAllowed, String authToken) {
        return !remoteExposure || (remoteAllowed && authToken != null && !authToken.isBlank());
    }

    static boolean isAuthorized(String authorizationHeader, String expectedToken) {
        if (authorizationHeader == null || expectedToken == null || expectedToken.isBlank()) return false;
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) return false;
        String suppliedToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (suppliedToken.isEmpty()) return false;
        return MessageDigest.isEqual(
                suppliedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private String renderMetrics() {
        ServerMetrics.Snapshot snapshot = metrics.snapshot();
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long dbTotalConnections = 0;
        long dbIdleConnections = 0;
        long dbMaxConnections = 0;
        try {
            ConnectionPool connectionPool = ConnectionPool.getInstance();
            dbTotalConnections = connectionPool.getTotalConnections();
            dbIdleConnections = connectionPool.getIdleConnections();
            dbMaxConnections = connectionPool.getMaxSize();
        } catch (RuntimeException e) {
            logger.warn("Database metrics unavailable ({}).", e.getClass().getSimpleName());
        }

        StringBuilder output = new StringBuilder(3600);
        appendGauge(output, "chatapp_connected_users", "Currently connected authenticated users.", server.connectedUserCount());
        appendGauge(output, "chatapp_active_handlers", "Currently active client handlers.", server.activeHandlerCount());
        appendCounter(output, "chatapp_accepted_connections_total", "Accepted client connections.", snapshot.acceptedConnections());
        appendCounter(output, "chatapp_rejected_connections_total", "Rejected client connections.", snapshot.rejectedConnections());
        appendCounter(output, "chatapp_requests_total", "Processed client requests.", snapshot.requests());
        appendCounter(output, "chatapp_protocol_errors_total", "Protocol errors observed.", snapshot.protocolErrors());
        appendCounter(output, "chatapp_authentication_failures_total", "Authentication failures observed.", snapshot.authenticationFailures());
        appendCounter(output, "chatapp_rate_limited_requests_total", "Requests rejected by application rate limits.", snapshot.rateLimitedRequests());
        appendCounter(output, "chatapp_internal_errors_total", "Internal request-handler errors observed.", snapshot.internalErrors());
        appendGauge(output, "chatapp_handler_pool_active", "Active client handler executor tasks.", server.handlerPoolActiveCount());
        appendGauge(output, "chatapp_handler_pool_size", "Current client handler executor pool size.", server.handlerPoolSize());
        appendGauge(output, "chatapp_handler_pool_queue_depth", "Queued client handler tasks.", server.handlerPoolQueueDepth());
        appendCounter(output, "chatapp_handler_pool_completed_total", "Completed client handler tasks.", server.completedHandlerCount());
        appendGauge(output, "chatapp_db_pool_connections", "Database connections currently created.", dbTotalConnections);
        appendGauge(output, "chatapp_db_pool_idle_connections", "Database connections currently available for borrowing.", dbIdleConnections);
        appendGauge(output, "chatapp_db_pool_max_connections", "Configured maximum database connections.", dbMaxConnections);
        appendGauge(output, "chatapp_jvm_memory_used_bytes", "JVM heap memory currently used.", runtime.totalMemory() - runtime.freeMemory());
        appendGauge(output, "chatapp_jvm_memory_committed_bytes", "JVM heap memory currently committed.", runtime.totalMemory());
        appendGauge(output, "chatapp_jvm_memory_max_bytes", "Maximum JVM heap memory available.", runtime.maxMemory());
        appendGauge(output, "chatapp_jvm_uptime_seconds", "JVM uptime in seconds.", runtimeBean.getUptime() / 1000);
        appendGauge(output, "chatapp_jvm_threads_live", "Currently live JVM threads.", threadBean.getThreadCount());
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
