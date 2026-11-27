package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatapp.service.AuthenticationService;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class ChatServerLifecycleTest {
    @Test
    void metricsSchedulerCanBeRecreatedAfterStop() throws Exception {
        ChatServer server = new ChatServer(new AuthenticationService());
        java.lang.reflect.Method startMetricsLogging = ChatServer.class.getDeclaredMethod("startMetricsLogging");
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

    @Test
    void ipv6WildcardPlaintextBindingTriggersWarningPolicy() throws Exception {
        InetAddress wildcard = InetAddress.getByName("::");

        assertTrue(ChatServer.isPlaintextRemoteBind(wildcard, false));
        assertFalse(ChatServer.isPlaintextRemoteBind(wildcard, true));
    }

    @Test
    void readinessHeartbeatRequiresRunningServerAndHealthyDatabase() {
        assertTrue(ChatServer.shouldRefreshReadiness(true, true));
        assertFalse(ChatServer.shouldRefreshReadiness(true, false));
        assertFalse(ChatServer.shouldRefreshReadiness(false, true));
        assertFalse(ChatServer.shouldRefreshReadiness(false, false));
    }
}
