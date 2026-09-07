package com.chatapp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Minimal TCP readiness probe used by the container health check. */
public final class ServerHealthCheck {
    private static final int DEFAULT_PORT = 5050;
    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;

    private ServerHealthCheck() {
    }

    public static void main(String[] args) {
        final int port;
        try {
            port = resolvePort();
        } catch (IllegalArgumentException e) {
            System.exit(1);
            return;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            System.exit(1);
        }
    }

    private static int resolvePort() {
        return parsePort(System.getenv("CHATAPP_SERVER_PORT"));
    }

    static int parsePort(String configuredPort) {
        if (configuredPort == null || configuredPort.isBlank()) {
            return DEFAULT_PORT;
        }
        final int port;
        try {
            port = Integer.parseInt(configuredPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CHATAPP_SERVER_PORT must be a valid TCP port.", e);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("CHATAPP_SERVER_PORT must be between 1 and 65535.");
        }
        return port;
    }
}
