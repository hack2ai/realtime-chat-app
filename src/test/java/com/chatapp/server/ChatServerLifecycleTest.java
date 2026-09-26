package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatapp.service.AuthenticationService;
import java.lang.reflect.Method;
import java.net.InetAddress;
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

    @Test
    void loopbackPlaintextBindingDoesNotTriggerRemoteWarning() {
        assertFalse(ChatServer.isPlaintextRemoteBind(InetAddress.getLoopbackAddress(), false));
    }

    @Test
    void remotePlaintextBindingTriggersWarningPolicy() throws Exception {
        InetAddress wildcard = InetAddress.getByName("0.0.0.0");

        assertTrue(ChatServer.isPlaintextRemoteBind(wildcard, false));
        assertFalse(ChatServer.isPlaintextRemoteBind(wildcard, true));
    }
}
