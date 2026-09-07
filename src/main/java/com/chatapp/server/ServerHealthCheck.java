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
        int port = resolvePort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            System.exit(1);
        }
    }

    private static int resolvePort() {
        String configuredPort = System.getenv("CHATAPP_SERVER_PORT");
        if (configuredPort == null || configuredPort.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(configuredPort);
            if (port < 1 || port > 65_535) {
                System.exit(1);
            }
            return port;
        } catch (NumberFormatException e) {
            System.exit(1);
            return DEFAULT_PORT;
        }
    }
}
