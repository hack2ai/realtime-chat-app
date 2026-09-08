package com.chatapp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Minimal TCP readiness probe used by the container health check. */
public final class ServerHealthCheck {
    private static final int DEFAULT_PORT = 5050;
    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final String PORT_PROPERTY = "chatapp.server.port";
    private static final String PORT_ENVIRONMENT = "CHATAPP_SERVER_PORT";

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
        String configuredPort = System.getProperty(PORT_PROPERTY);
        if (configuredPort != null && !configuredPort.isBlank()) {
            return parsePort(configuredPort.trim());
        }
        return parsePort(System.getenv(PORT_ENVIRONMENT));
    }

    static int parsePort(String configuredPort) {
        if (configuredPort == null || configuredPort.isBlank()) {
            return DEFAULT_PORT;
        }
        final int port;
        try {
            port = Integer.parseInt(configuredPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Configured server port must be a valid TCP port.", e);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Configured server port must be between 1 and 65535.");
        }
        return port;
    }
}
