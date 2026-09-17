package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.chatapp.service.AuthenticationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ChatServerLifecycleTest {
    @Test
    void metricsSchedulerCanBeRecreatedAfterStop() throws Exception {
        ChatServer server = new ChatServer(new AuthenticationService());
        Method startMetricsLogging = ChatServer.class.getDeclaredMethod("startMetricsLogging");
        startMetricsLogging.setAccessible(true);

        startMetricsLogging.invoke(server);
        server.stop();

        assertDoesNotThrow(() -> startMetricsLogging.invoke(server));
        server.stop();
    }
}
