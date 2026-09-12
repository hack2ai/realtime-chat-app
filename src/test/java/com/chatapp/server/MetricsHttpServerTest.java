package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MetricsHttpServerTest {
    @Test
    void metricsRendererProducesPrometheusCountersAndGauges() throws Exception {
        ServerMetrics metrics = new ServerMetrics();
        metrics.recordAcceptedConnection();
        metrics.recordRequest();
        metrics.recordProtocolError();
        metrics.recordAuthenticationFailure();
        metrics.recordRateLimitedRequest();
        metrics.recordInternalError();

        ChatServer server = new ChatServer(new com.chatapp.service.AuthenticationService());
        try {
            MetricsHttpServer metricsServer = new MetricsHttpServer(metrics, server);
            Method renderer = MetricsHttpServer.class.getDeclaredMethod("renderMetrics");
            renderer.setAccessible(true);

            String output = (String) renderer.invoke(metricsServer);

            assertTrue(output.contains("# TYPE chatapp_accepted_connections_total counter"));
            assertTrue(output.contains("chatapp_accepted_connections_total 1"));
            assertTrue(output.contains("# TYPE chatapp_rejected_connections_total counter"));
            assertTrue(output.contains("chatapp_rejected_connections_total 0"));
            assertTrue(output.contains("# TYPE chatapp_requests_total counter"));
            assertTrue(output.contains("chatapp_requests_total 1"));
            assertTrue(output.contains("# TYPE chatapp_protocol_errors_total counter"));
            assertTrue(output.contains("chatapp_protocol_errors_total 1"));
            assertTrue(output.contains("# TYPE chatapp_authentication_failures_total counter"));
            assertTrue(output.contains("chatapp_authentication_failures_total 1"));
            assertTrue(output.contains("# TYPE chatapp_rate_limited_requests_total counter"));
            assertTrue(output.contains("chatapp_rate_limited_requests_total 1"));
            assertTrue(output.contains("# TYPE chatapp_internal_errors_total counter"));
            assertTrue(output.contains("# HELP chatapp_internal_errors_total Internal request-handler errors observed."));
            assertTrue(output.contains("chatapp_internal_errors_total 1"));
            assertTrue(output.contains("# TYPE chatapp_connected_users gauge"));
            assertTrue(output.contains("# TYPE chatapp_active_handlers gauge"));
            assertTrue(output.contains("# TYPE chatapp_handler_pool_active gauge"));
            assertTrue(output.contains("# TYPE chatapp_handler_pool_size gauge"));
            assertTrue(output.contains("# TYPE chatapp_handler_pool_queue_depth gauge"));
            assertTrue(output.contains("# TYPE chatapp_handler_pool_completed_total counter"));
            assertTrue(output.contains("# TYPE chatapp_jvm_memory_used_bytes gauge"));
            assertTrue(output.contains("# TYPE chatapp_jvm_memory_committed_bytes gauge"));
            assertTrue(output.contains("# TYPE chatapp_jvm_memory_max_bytes gauge"));
            assertTrue(output.contains("# TYPE chatapp_jvm_uptime_seconds gauge"));
            assertTrue(output.contains("# TYPE chatapp_jvm_threads_live gauge"));
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsExactBearerToken() {
        assertTrue(MetricsHttpServer.isAuthorized("Bearer metrics-secret", "metrics-secret"));
    }

    @Test
    void rejectsMissingMalformedOrIncorrectBearerToken() {
        assertFalse(MetricsHttpServer.isAuthorized(null, "metrics-secret"));
        assertFalse(MetricsHttpServer.isAuthorized("metrics-secret", "metrics-secret"));
        assertFalse(MetricsHttpServer.isAuthorized("Basic metrics-secret", "metrics-secret"));
        assertFalse(MetricsHttpServer.isAuthorized("Bearer wrong", "metrics-secret"));
        assertFalse(MetricsHttpServer.isAuthorized("Bearer ", "metrics-secret"));
    }

    @Test
    void tokenComparisonRequiresExactUtf8Value() {
        assertTrue(MetricsHttpServer.isAuthorized("Bearer café", "café"));
        assertFalse(MetricsHttpServer.isAuthorized("Bearer cafe", "café"));
    }

    @Test
    void remoteMetricsRequireExplicitOptInAndAuthentication() {
        assertTrue(MetricsHttpServer.isRemoteExposureAllowed(false, false, ""));
        assertTrue(MetricsHttpServer.isRemoteExposureAllowed(false, true, ""));
        assertFalse(MetricsHttpServer.isRemoteExposureAllowed(true, false, "metrics-secret"));
        assertFalse(MetricsHttpServer.isRemoteExposureAllowed(true, true, ""));
        assertTrue(MetricsHttpServer.isRemoteExposureAllowed(true, true, "metrics-secret"));
    }
}
