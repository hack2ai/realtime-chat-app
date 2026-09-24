package com.chatapp.client;

import com.chatapp.model.dto.AuthDTOs.AuthFailedResponse;
import com.chatapp.model.dto.AuthDTOs.LoginRequest;
import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterRequest;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * A bare command-line client for exercising the server's authentication
 * flow without needing the JavaFX UI.
 *
 * <p>This is a development/testing aid, not part of the end-user
 * product — its job is to prove the wire protocol, codec framing,
 * and {@code AuthenticationService} integration all work together
 * over a real socket, end to end. Run {@code ChatServer} first.
 *
 * <p>Usage:
 * <pre>
 *   java -cp target/classes com.chatapp.client.TestClient register alice alice@example.com Passw0rd1 Passw0rd1
 *   java -cp target/classes com.chatapp.client.TestClient login alice Passw0rd1
 * </pre>
 */
public class TestClient {

    private static final String GENERIC_LOGIN_FAILURE = "Invalid username/email or password.";

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
                    System.out.println("Received: " + response.getType());
                }
                case "auth-smoke" -> runAuthSmoke(codec, in, out);
                default -> printUsageAndExit();
            }
        }
    }

    private static void runAuthSmoke(MessageCodec codec, DataInputStream in, DataOutputStream out) throws IOException {
        String suffix = Long.toUnsignedString(System.currentTimeMillis());
        String username = "smoke" + suffix;
        String email = username + "@example.com";
        String password = "SmokePass9";

        codec.write(out, codec.wrap(MessageType.C2S_REGISTER,
                new RegisterRequest(username, email, password, password)));
        Envelope registerResponse = codec.read(in);
        if (registerResponse == null || registerResponse.getType() != MessageType.S2C_REGISTER_SUCCESS) {
            throw new IOException("Authentication smoke test registration failed.");
        }

        codec.write(out, codec.wrap(MessageType.C2S_LOGIN,
                new LoginRequest(username, "WrongPass9")));
        Envelope wrongPasswordResponse = codec.read(in);
        assertGenericLoginFailure(codec, wrongPasswordResponse, "wrong password");

        codec.write(out, codec.wrap(MessageType.C2S_LOGIN,
                new LoginRequest("missing" + suffix, password)));
        Envelope missingUserResponse = codec.read(in);
        assertGenericLoginFailure(codec, missingUserResponse, "unknown account");

        AuthFailedResponse wrongPassword = codec.unwrap(wrongPasswordResponse, AuthFailedResponse.class);
        AuthFailedResponse missingUser = codec.unwrap(missingUserResponse, AuthFailedResponse.class);
        if (!GENERIC_LOGIN_FAILURE.equals(wrongPassword.getReason())
                || !GENERIC_LOGIN_FAILURE.equals(missingUser.getReason())
                || !wrongPassword.getReason().equals(missingUser.getReason())) {
            throw new IOException("Authentication smoke test exposed inconsistent login failure responses.");
        }

        codec.write(out, codec.wrap(MessageType.C2S_LOGIN,
                new LoginRequest(username, password)));
        Envelope loginResponse = codec.read(in);
        if (loginResponse == null || loginResponse.getType() != MessageType.S2C_LOGIN_SUCCESS) {
            throw new IOException("Authentication smoke test login failed.");
        }

        LoginSuccessResponse login = codec.unwrap(loginResponse, LoginSuccessResponse.class);
        if (login == null || login.getUserId() <= 0
                || login.getSessionToken() == null || login.getSessionToken().isBlank()) {
            throw new IOException("Authentication smoke test returned an invalid session.");
        }

        System.out.println("Authentication smoke test passed.");
    }

    private static void assertGenericLoginFailure(MessageCodec codec, Envelope response, String scenario)
            throws IOException {
        if (response == null || response.getType() != MessageType.S2C_LOGIN_FAILED) {
            throw new IOException("Authentication smoke test " + scenario + " unexpectedly succeeded.");
        }
        AuthFailedResponse failure = codec.unwrap(response, AuthFailedResponse.class);
        if (failure == null || !GENERIC_LOGIN_FAILURE.equals(failure.getReason())) {
            throw new IOException("Authentication smoke test " + scenario + " returned a non-generic failure.");
        }
    }

    private static void handleAuthResponse(MessageCodec codec, DataInputStream in) throws IOException {
        Envelope response = codec.read(in);

        switch (response.getType()) {
            case S2C_REGISTER_SUCCESS -> {
                RegisterSuccessResponse r = codec.unwrap(response, RegisterSuccessResponse.class);
                System.out.println("✓ Registered successfully: userId=" + r.getUserId() + ", username=" + r.getUsername());
            }
            case S2C_LOGIN_SUCCESS -> {
                LoginSuccessResponse r = codec.unwrap(response, LoginSuccessResponse.class);
                System.out.println("✓ Login successful:");
                System.out.println("    userId:  " + r.getUserId());
                System.out.println("    username: " + r.getUsername());
                System.out.println("    role:     " + r.getRole());
                System.out.println("    token:    " + r.getSessionToken());
            }
            case S2C_REGISTER_FAILED, S2C_LOGIN_FAILED, S2C_ERROR -> {
                AuthFailedResponse r = codec.unwrap(response, AuthFailedResponse.class);
                System.out.println("✗ Failed: " + r.getReason());
            }
            default -> System.out.println("Unexpected response type: " + response.getType());
        }
    }

    private static void requireArgs(String[] args, int minLength, String usage) {
        if (args.length < minLength) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
    }

    private static void printUsageAndExit() {
        System.err.println("""
                Usage:
                  register <username> <email> <password> <confirmPassword>
                  login <usernameOrEmail> <password>
                  ping
                  auth-smoke
                """);
        System.exit(1);
    }
}
