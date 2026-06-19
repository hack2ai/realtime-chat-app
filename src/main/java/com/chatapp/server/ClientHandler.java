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

/**
 * Handles one client's connection lifecycle on a dedicated thread:
 * reads {@link Envelope} messages, dispatches them by
 * {@link MessageType}, and writes responses back.
 *
 * <p>Phase 1 implements the full authentication flow
 * (register/login/logout) end-to-end — this is deliberately more than
 * "just a skeleton" so the server is genuinely runnable and testable
 * right now, e.g. with a raw socket test client, before any chat
 * messaging exists. Private/group messaging dispatch cases are added
 * in Phase 2/3; until then, an authenticated client that sends those
 * message types receives a {@code S2C_ERROR} "not yet implemented"
 * response rather than the server silently ignoring the message or
 * crashing the connection.
 */
public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ChatServer server;
    private final AuthenticationService authService;
    private final MessageCodec codec = new MessageCodec();

    private DataInputStream in;
    private DataOutputStream out;

    /** Set once login succeeds; -1 means not yet authenticated. */
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
            logger.warn("I/O error on connection from {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void messageLoop() throws IOException {
        while (!socket.isClosed()) {
            Envelope envelope;
            try {
                envelope = codec.read(in);
            } catch (EOFException e) {
                // Clean disconnect — the peer closed the connection.
                // Not an error condition; just exit the loop.
                logger.info("Client disconnected: {}", socket.getRemoteSocketAddress());
                return;
            }

            try {
                dispatch(envelope);
            } catch (Exception e) {
                // Catch broadly here deliberately: a bug while handling
                // one malformed/unexpected message must not kill the
                // entire connection thread (which would silently drop
                // the client) or, worse, propagate and take down shared
                // state. Report it back to the client as an error and
                // keep the connection alive.
                logger.error("Error handling message type {} from {}: {}",
                        envelope.getType(), socket.getRemoteSocketAddress(), e.getMessage(), e);
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

            // --- Everything below requires authentication first ---
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
                    // Phase 2/3 will replace this with real handlers.
                    sendError("This feature (" + type + ") is not yet implemented in this build.");
                }
            }

            default -> {
                logger.warn("Received unhandled message type: {}", type);
                sendError("Unsupported message type: " + type);
            }
        }
    }

    // ---------------------------------------------------------------
    // Authentication handlers
    // ---------------------------------------------------------------

    private void handleRegister(Envelope envelope) throws IOException {
        RegisterRequest req = codec.unwrap(envelope, RegisterRequest.class);
        try {
            User created = authService.register(
                    req.getUsername(), req.getEmail(), req.getPassword(), req.getConfirmPassword()
            );
            send(MessageType.S2C_REGISTER_SUCCESS,
                    new RegisterSuccessResponse(created.getId(), created.getUsername()));

        } catch (ValidationException e) {
            send(MessageType.S2C_REGISTER_FAILED, new AuthFailedResponse(e.getMessage()));
        }
    }

    private void handleLogin(Envelope envelope) throws IOException {
        LoginRequest req = codec.unwrap(envelope, LoginRequest.class);
        try {
            AuthenticationService.LoginResult result =
                    authService.login(req.getUsernameOrEmail(), req.getPassword());

            this.authenticatedUserId = result.user().getId();
            this.sessionToken = result.sessionToken();
            server.registerClient(authenticatedUserId, this);

            send(MessageType.S2C_LOGIN_SUCCESS, new LoginSuccessResponse(
                    result.user().getId(),
                    result.user().getUsername(),
                    result.user().getRole().name(),
                    result.sessionToken()
            ));

            logger.info("User '{}' authenticated successfully from {}",
                    result.user().getUsername(), socket.getRemoteSocketAddress());

        } catch (AuthenticationException e) {
            send(MessageType.S2C_LOGIN_FAILED, new AuthFailedResponse(e.getMessage()));
        }
    }

    private void handleLogout() throws IOException {
        if (authenticatedUserId != -1) {
            authService.logout(sessionToken);
            server.deregisterClient(authenticatedUserId);
            authenticatedUserId = -1;
            sessionToken = null;
        }
        send(MessageType.S2C_LOGOUT_ACK, null);
    }

    // ---------------------------------------------------------------
    // Outbound helpers
    // ---------------------------------------------------------------

    /**
     * Sends a message to THIS handler's client. Package-visible (not
     * private) so {@code ChatServer}'s future message-routing logic
     * (Phase 2) can push a message to another user by calling
     * {@code otherHandler.send(...)} after looking up their handler via
     * {@link ChatServer#getHandler(int)}.
     */
    void send(MessageType type, Object payload) throws IOException {
        Envelope envelope = codec.wrap(type, payload);
        codec.write(out, envelope);
    }

    private void sendError(String message) {
        try {
            send(MessageType.S2C_ERROR, new AuthFailedResponse(message));
        } catch (IOException e) {
            logger.warn("Failed to send error response to client: {}", e.getMessage());
        }
    }

    public int getAuthenticatedUserId() {
        return authenticatedUserId;
    }

    private void cleanup() {
        if (authenticatedUserId != -1) {
            server.deregisterClient(authenticatedUserId);
            authService.logout(sessionToken);
        }
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("Error closing socket for {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
        }
    }
}
