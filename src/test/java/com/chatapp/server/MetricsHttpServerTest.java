package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MetricsHttpServerTest {
    @Test
    void metricsRendererProducesPrometheusCountersAndGauges() throws Exception {
        ServerMetrics metrics = new ServerMetrics();
        metrics.recordAcceptedConnection();
        metrics.recordRequest();
        metrics.recordProtocolError();

        MetricsHttpServer metricsServer = new MetricsHttpServer(metrics, new ChatServer(new com.chatapp.service.AuthenticationService()));
        Method renderer = MetricsHttpServer.class.getDeclaredMethod("renderMetrics");
        renderer.setAccessible(true);

        String output = (String) renderer.invoke(metricsServer);

        assertTrue(output.contains("# TYPE chatapp_accepted_connections_total counter"));
        assertTrue(output.contains("chatapp_accepted_connections_total 1"));
        assertTrue(output.contains("# TYPE chatapp_connected_users gauge"));
        assertTrue(output.contains("# TYPE chatapp_jvm_uptime_seconds gauge"));
        assertTrue(output.contains("chatapp_protocol_errors_total 1"));
        assertTrue(output.contains("chatapp_requests_total 1"));
    }
}
