package com.chatapp.server;

import com.chatapp.exception.AuthenticationException;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.User;
import com.chatapp.model.dto.AuthDTOs.AuthFailedResponse;
import com.chatapp.model.dto.AuthDTOs.LoginRequest;
import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterRequest;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.service.AuthenticationService;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

/** Handles one client's connection lifecycle and protocol dispatch. */
public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ChatServer server;
    private final AuthenticationService authService;
    private final MessageCodec codec = new MessageCodec();

    private DataInputStream in;
    private DataOutputStream out;
    private volatile int authenticatedUserId = -1;
    private volatile String sessionToken;

    public ClientHandler(Socket socket, ChatServer server, AuthenticationService authService) {
        this.socket = socket;
        this.server = server;
        this.authService = authService;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            messageLoop();
        } catch (IOException e) {
            logger.warn("I/O error on {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void messageLoop() throws IOException {
        while (!socket.isClosed()) {
            final Envelope envelope;
            try {
                envelope = codec.read(in);
            } catch (EOFException e) {
                logger.info("Client disconnected: {}", socket.getRemoteSocketAddress());
                return;
            } catch (RuntimeException e) {
                logger.warn("Invalid protocol message from {}; closing connection: {}",
                        socket.getRemoteSocketAddress(), e.getMessage());
                return;
            }

            if (envelope == null || envelope.getType() == null) {
                sendError("Invalid message envelope.");
                continue;
            }

            try {
                dispatch(envelope);
            } catch (Exception e) {
                logger.error("Error handling {} from {}", envelope.getType(),
                        socket.getRemoteSocketAddress(), e);
                sendError("An internal error occurred processing your request.");
            }
        }
    }

    private void dispatch(Envelope envelope) throws IOException {
        MessageType type = envelope.getType();
        switch (type) {
            case PING -> send(MessageType.PONG, null);
            case C2S_REGISTER -> handleRegister(envelope);
            case C2S_LOGIN -> handleLogin(envelope);
            case C2S_LOGOUT -> handleLogout();
            case C2S_REQUEST_USER_LIST,
                 C2S_PRIVATE_MESSAGE,
                 C2S_MESSAGE_READ,
                 C2S_REQUEST_PRIVATE_HISTORY,
                 C2S_TYPING_START,
                 C2S_TYPING_STOP,
                 C2S_CREATE_GROUP,
                 C2S_JOIN_GROUP,
                 C2S_LEAVE_GROUP,
                 C2S_GROUP_MESSAGE,
                 C2S_REQUEST_GROUP_LIST,
                 C2S_REQUEST_GROUP_HISTORY -> {
                if (authenticatedUserId == -1) {
                    sendError("You must log in before sending this message type.");
                } else {
                    sendError("This feature (" + type + ") is not yet implemented in this build.");
                }
            }
            default -> sendError("Unsupported message type: " + type);
        }
    }

    private void handleRegister(Envelope envelope) throws IOException {
        RegisterRequest req = codec.unwrap(envelope, RegisterRequest.class);
        if (req == null) {
            sendError("Invalid registration request.");
            return;
        }
        try {
            User created = authService.register(
                    req.getUsername(), req.getEmail(), req.getPassword(), req.getConfirmPassword());
            send(MessageType.S2C_REGISTER_SUCCESS,
                    new RegisterSuccessResponse(created.getId(), created.getUsername()));
        } catch (ValidationException e) {
            send(MessageType.S2C_REGISTER_FAILED, new AuthFailedResponse(e.getMessage()));
        }
    }

    private void handleLogin(Envelope envelope) throws IOException {
        if (authenticatedUserId != -1) {
            sendError("This connection is already authenticated.");
            return;
        }

        LoginRequest req = codec.unwrap(envelope, LoginRequest.class);
        if (req == null) {
            sendError("Invalid login request.");
            return;
        }

        try {
            AuthenticationService.LoginResult result =
                    authService.login(req.getUsernameOrEmail(), req.getPassword());

            if (!server.registerClient(result.user().getId(), this)) {
                authService.logout(result.sessionToken());
                send(MessageType.S2C_LOGIN_FAILED,
                        new AuthFailedResponse("This account is already connected."));
                return;
            }

            authenticatedUserId = result.user().getId();
            sessionToken = result.sessionToken();

            send(MessageType.S2C_LOGIN_SUCCESS, new LoginSuccessResponse(
                    result.user().getId(),
                    result.user().getUsername(),
                    result.user().getRole().name(),
                    result.sessionToken()));

            logger.info("User '{}' authenticated from {}", result.user().getUsername(),
                    socket.getRemoteSocketAddress());
        } catch (AuthenticationException e) {
            send(MessageType.S2C_LOGIN_FAILED, new AuthFailedResponse(e.getMessage()));
        }
    }

    private void handleLogout() throws IOException {
        if (authenticatedUserId != -1) {
            int userId = authenticatedUserId;
            String token = sessionToken;
            authenticatedUserId = -1;
            sessionToken = null;
            server.deregisterClient(userId, this);
            authService.logout(token);
        }
        send(MessageType.S2C_LOGOUT_ACK, null);
    }

    /** Sends a framed message to this client's socket. */
    void send(MessageType type, Object payload) throws IOException {
        Envelope envelope = codec.wrap(type, payload);
        codec.write(out, envelope);
    }

    private void sendError(String message) {
        try {
            send(MessageType.S2C_ERROR, new AuthFailedResponse(message));
        } catch (IOException e) {
            logger.debug("Unable to send error response to {}", socket.getRemoteSocketAddress());
        }
    }

    public int getAuthenticatedUserId() {
        return authenticatedUserId;
    }

    private void cleanup() {
        int userId = authenticatedUserId;
        String token = sessionToken;
        authenticatedUserId = -1;
        sessionToken = null;

        if (userId != -1) {
            server.deregisterClient(userId, this);
            authService.logout(token);
        }

        try {
            socket.close();
        } catch (IOException e) {
            logger.debug("Error closing socket for {}", socket.getRemoteSocketAddress());
        }
    }
}
