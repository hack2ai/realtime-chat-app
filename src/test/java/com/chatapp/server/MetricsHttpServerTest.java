package com.chatapp.server;

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
}
