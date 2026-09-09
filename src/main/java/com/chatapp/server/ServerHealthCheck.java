package com.chatapp.server;

import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Minimal protocol health probe used by the container health check. */
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
            port = resolvePort(System.getProperty(PORT_PROPERTY), System.getenv(PORT_ENVIRONMENT));
        } catch (IllegalArgumentException e) {
            System.exit(1);
            return;
        }

        if (!isProtocolResponsive(port)) {
            System.exit(1);
        }
    }

    static boolean isProtocolResponsive(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MILLIS);

            MessageCodec codec = new MessageCodec();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            codec.write(output, codec.wrap(MessageType.PING, null));
            Envelope response = codec.read(input);
            return response.getType() == MessageType.PONG;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static int resolvePort(String systemPropertyPort, String environmentPort) {
        if (systemPropertyPort != null && !systemPropertyPort.isBlank()) {
            return parsePort(systemPropertyPort.trim());
        }
        return parsePort(environmentPort);
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
