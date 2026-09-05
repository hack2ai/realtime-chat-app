package com.chatapp.client;

import com.chatapp.model.dto.AuthDTOs.AuthFailedResponse;
import com.chatapp.model.dto.AuthDTOs.LoginRequest;
import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterRequest;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * A bare command-line client for exercising the server's authentication
 * flow without needing the JavaFX UI (which doesn't exist until Phase 4).
 *
 * <p>This is a development/testing aid, not part of the end-user
 * product — its only job is to prove the wire protocol, codec framing,
 * and {@code AuthenticationService} integration all work correctly
 * together over a real socket, end to end. Run {@code ChatServer} first,
 * then run this class with arguments to register or log in.
 *
 * <p>Usage:
 * <pre>
 *   java -cp target/classes com.chatapp.client.TestClient register alice alice@example.com Passw0rd1 Passw0rd1
 *   java -cp target/classes com.chatapp.client.TestClient login alice Passw0rd1
 * </pre>
 */
public class TestClient {
    private static final Logger logger = LoggerFactory.getLogger(TestClient.class);

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsageAndExit();
        }

        String command = args[0];
        MessageCodec codec = new MessageCodec();

        try (Socket socket = new Socket("localhost", 5050)) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            switch (command) {
                case "register" -> {
                    requireArgs(args, 5, "register <username> <email> <password> <confirmPassword>");
                    RegisterRequest req = new RegisterRequest(args[1], args[2], args[3], args[4]);
                    codec.write(out, codec.wrap(MessageType.C2S_REGISTER, req));
                    handleAuthResponse(codec, in);
                }
                case "login" -> {
                    requireArgs(args, 3, "login <usernameOrEmail> <password>");
                    LoginRequest req = new LoginRequest(args[1], args[2]);
                    codec.write(out, codec.wrap(MessageType.C2S_LOGIN, req));
                    handleAuthResponse(codec, in);
                }
                case "ping" -> {
                    codec.write(out, codec.wrap(MessageType.PING, null));
                    Envelope response = codec.read(in);
                    logger.info("Received: {}", response.getType());
                }
                default -> printUsageAndExit();
            }
        }
    }

    private static void handleAuthResponse(MessageCodec codec, DataInputStream in) throws IOException {
        Envelope response = codec.read(in);

        switch (response.getType()) {
            case S2C_REGISTER_SUCCESS -> {
                RegisterSuccessResponse r = codec.unwrap(response, RegisterSuccessResponse.class);
                logger.info("Registered successfully: userId={}, username={}", r.getUserId(), r.getUsername());
            }
            case S2C_LOGIN_SUCCESS -> {
                LoginSuccessResponse r = codec.unwrap(response, LoginSuccessResponse.class);
                logger.info("Login successful:");
                logger.info("  userId: {}", r.getUserId());
                logger.info("  username: {}", r.getUsername());
                logger.info("  role: {}", r.getRole());
                logger.info("  token: {}", r.getSessionToken());
            }
            case S2C_REGISTER_FAILED, S2C_LOGIN_FAILED, S2C_ERROR -> {
                AuthFailedResponse r = codec.unwrap(response, AuthFailedResponse.class);
                logger.warn("Authentication failed: {}", r.getReason());
            }
            default -> logger.warn("Unexpected response type: {}", response.getType());
        }
    }

    private static void requireArgs(String[] args, int minLength, String usage) {
        if (args.length < minLength) {
            logger.error("Usage: {}", usage);
            System.exit(1);
        }
    }

    private static void printUsageAndExit() {
        logger.error("""
                Usage:
                  register <username> <email> <password> <confirmPassword>
                  login <usernameOrEmail> <password>
                  ping
                """);
        System.exit(1);
    }
}
