package com.chatapp.server;

import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerHealthCheckTest {
    @Test
    void blankPortUsesDefault() {
        assertEquals(5050, ServerHealthCheck.parsePort(null));
        assertEquals(5050, ServerHealthCheck.parsePort(""));
        assertEquals(5050, ServerHealthCheck.parsePort("   "));
    }

    @Test
    void acceptsTcpPortBoundaries() {
        assertEquals(1, ServerHealthCheck.parsePort("1"));
        assertEquals(65_535, ServerHealthCheck.parsePort("65535"));
    }

    @Test
    void rejectsNonNumericPort() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("not-a-port"));
    }

    @Test
    void rejectsPortsOutsideTcpRange() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("0"));
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.parsePort("-1"));
    }

    @Test
    void systemPropertyTakesPrecedenceOverEnvironment() {
        assertEquals(6000, ServerHealthCheck.resolvePort(" 6000 ", "7000"));
    }

    @Test
    void invalidSystemPropertyCannotBeBypassedByEnvironment() {
        assertThrows(IllegalArgumentException.class, () -> ServerHealthCheck.resolvePort("not-a-port", "7000"));
    }

    @Test
    void environmentValueIsUsedWhenPropertyIsBlank() {
        assertEquals(7000, ServerHealthCheck.resolvePort("   ", "7000"));
    }

    @Test
    void defaultIsUsedWhenBothOverridesAreBlank() {
        assertEquals(5050, ServerHealthCheck.resolvePort(null, ""));
    }

    @Test
    void respondsToProtocolPing() throws Exception {
        MessageCodec codec = new MessageCodec();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            Thread serverThread = Thread.startVirtualThread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    Envelope request = codec.read(input);
                    assertEquals(MessageType.PING, request.getType());
                    codec.write(output, codec.wrap(MessageType.PONG, null));
                } catch (Throwable e) {
                    serverFailure.set(e);
                }
            });

            assertTrue(ServerHealthCheck.isProtocolResponsive(serverSocket.getLocalPort()));
            serverThread.join(3_000);
            assertFalse(serverThread.isAlive());
            if (serverFailure.get() != null) {
                throw new AssertionError("Health-check test server failed", serverFailure.get());
            }
        }
    }

    @Test
    void closedPortIsNotProtocolResponsive() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            serverSocket.close();
            assertFalse(ServerHealthCheck.isProtocolResponsive(port));
        }
    }

    @Test
    void nonPongResponseIsUnhealthy() throws Exception {
        MessageCodec codec = new MessageCodec();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread serverThread = Thread.startVirtualThread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    codec.read(input);
                    codec.write(output, codec.wrap(MessageType.S2C_ERROR, null));
                } catch (IOException ignored) {
                }
            });

            assertFalse(ServerHealthCheck.isProtocolResponsive(serverSocket.getLocalPort()));
            serverThread.join(3_000);
            assertFalse(serverThread.isAlive());
        }
    }
}
