package com.chatapp.server;

import com.chatapp.exception.AuthenticationException;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.User;
import com.chatapp.model.dto.AuthDTOs.AuthFailedResponse;
import com.chatapp.model.dto.AuthDTOs.LoginRequest;
import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterRequest;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.model.dto.ChatDTOs.MessageReadRequest;
import com.chatapp.model.dto.ChatDTOs.PrivateHistoryRequest;
import com.chatapp.model.dto.ChatDTOs.PrivateHistoryResponse;
import com.chatapp.model.dto.ChatDTOs.PrivateMessageEvent;
import com.chatapp.model.dto.ChatDTOs.PrivateMessageRequest;
import com.chatapp.model.dto.ChatDTOs.TypingEvent;
import com.chatapp.model.dto.ChatDTOs.UserListResponse;
import com.chatapp.model.dto.GroupDTOs.CreateGroupRequest;
import com.chat2app.model.dto.GroupDTOs.GroupCreatedResponse;
import com.chatapp.model.dto.GroupDTOs.GroupHistoryRequest;
import com.chatapp.model.dto.GroupDTOs.GroupHistoryResponse;
import com.chatapp.model.dto.GroupDTOs.GroupJoinRequest;
import com.chatapp.model.dto.GroupDTOs.GroupListResponse;
import com.chatapp.model.dto.GroupDTOs.GroupMessageEvent;
import com.chatapp.model.dto.GroupDTOs.GroupMessageRequest;
import com.chatapp.model.dto.GroupDTOs.GroupSummary;
import com.chatapp.service.AuthenticationService;
import com.chatapp.service.ChatService;
import com.chatapp.service.GroupService;
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
import java.util.OptionalInt;

/** Handles one client's connection lifecycle and protocol dispatch. */
public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket socket;
    private final ChatServer server;
    private final AuthenticationService authService;
    private final ChatService chatService;
    private final GroupService groupService;
    private final MessageCodec codec = new MessageCodec();
    private DataInputStream in;
    private DataOutputStream out;
    private volatile int authenticatedUserId = -1;
    private volatile String authenticatedUsername;
    private volatile String sessionToken;

    public ClientHandler(Socket socket, ChatServer server, AuthenticationService authService) {
        this(socket, server, authService, new ChatService(), new GroupService());
    }
    public ClientHandler(Socket socket, ChatServer server, AuthenticationService authService, ChatService chatService) {
        this(socket, server, authService, chatService, new GroupService());
    }
    public ClientHandler(Socket socket, ChatServer server, AuthenticationService authService, ChatService chatService, GroupService groupService) {
        this.socket = socket;
        this.server = server;
        this.authService = authService;
        this.chatService = chatService;
        this.groupService = groupService;
    }

    @Override public void run() {
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
            try { envelope = codec.read(in); }
            catch (EOFException e) { logger.info("Client disconnected: {}", socket.getRemoteSocketAddress()); return; }
            catch (RuntimeException e) {
                logger.warn("Invalid protocol message from {}; closing connection: {}", socket.getRemoteSocketAddress(), e.getMessage());
                return;
            }
            if (envelope == null || envelope.getType() == null) { sendError("Invalid message envelope."); continue; }
            try { dispatch(envelope); }
            catch (Exception e) {
                logger.error("Error handling {} from {}", envelope.getType(), socket.getRemoteSocketAddress(), e);
                sendError("An internal error occurred processing your request.");
            }
        }
    }

    private void dispatch(Envelope envelope) throws Exception {
        switch (envelope.getType()) {
            case PING -> send(MessageType.PONG, null);
            case C2S_REGISTER -> handleRegister(envelope);
            case C2S_LOGIN -> handleLogin(envelope);
            case C2S_LOGOUT -> handleLogout();
            case C2S_REQUEST_USER_LIST -> requireAuth(() -> send(MessageType.S2C_USER_LIST,
                    new UserListResponse(chatService.listUsers(authenticatedUserId))));
            case C2S_PRIVATE_MESSAGE -> requireAuth(() -> handlePrivateMessage(envelope));
            case C2S_REQUEST_PRIVATE_HISTORY -> requireAuth(() -> handlePrivateHistory(envelope));
            case C2S_MESSAGE_READ -> requireAuth(() -> handleMessageRead(envelope));
            case C2S_TYPING_START -> requireAuth(() -> handleTyping(envelope, MessageType.S2C_TYPING_START));
            case C2S_TYPING_STOP -> requireAuth(() -> handleTyping(envelope, MessageType.S2C_TYPING_STOP));
            case C2S_CREATE_GROUP -> requireAuth(() -> handleCreateGroup(envelope));
            case C2S_JOIN_GROUP -> requireAuth(() -> handleJoinGroup(envelope));
            case C2S_LEAVE_GROUP -> requireAuth(() -> handleLeaveGroup(envelope));
            case C2S_GROUP_MESSAGE -> requireAuth(() -> handleGroupMessage(envelope));
            case C2S_REQUEST_GROUP_LIST -> requireAuth(() -> send(MessageType.S2C_GROUP_LIST,
                    new GroupListResponse(groupService.list(authenticatedUserId))));
            case C2S_REQUEST_GROUP_HISTORY -> requireAuth(() -> handleGroupHistory(envelope));
            default -> sendError("Unsupported message type: " + envelope.getType());
        }
    }

    @FunctionalInterface private interface HandlerAction { void run() throws Exception; }
    private void requireAuth(HandlerAction action) throws Exception {
        if (authenticatedUserId == -1) { sendError("You must log in before sending this message type."); return; }
        action.run();
    }

    private void handleRegister(Envelope envelope) throws IOException {
        RegisterRequest req = codec.unwrap(envelope, RegisterRequest.class);
        if (req == null) { sendError("Invalid registration request."); return; }
        try {
            User created = authService.register(req.getUsername(), req.getEmail(), req.getPassword(), req.getConfirmPassword());
            send(MessageType.S2C_REGISTER_SUCCESS, new RegisterSuccessResponse(created.getId(), created.getUsername()));
        } catch (ValidationException e) { send(MessageType.S2C_REGISTER_FAILED, new AuthFailedResponse(e.getMessage())); }
    }

    private void handleLogin(Envelope envelope) throws IOException {
        if (authenticatedUserId != -1) { sendError("This connection is already authenticated."); return; }
        LoginRequest req = codec.unwrap(envelope, LoginRequest.class);
        if (req == null) { sendError("Invalid login request."); return; }
        try {
            AuthenticationService.LoginResult result = authService.login(req.getUsernameOrEmail(), req.getPassword());
            authenticatedUserId = result.user().getId(); authenticatedUsername = result.user().getUsername(); sessionToken = result.sessionToken();
            if (!server.registerClient(authenticatedUserId, this)) {
                authenticatedUserId = -1; authenticatedUsername = null; authService.logout(result.sessionToken());
                send(MessageType.S2C_LOGIN_FAILED, new AuthFailedResponse("This account is already connected.")); return;
            }
            send(MessageType.S2C_LOGIN_SUCCESS, new LoginSuccessResponse(result.user().getId(), result.user().getUsername(), result.user().getRole().name(), result.sessionToken()));
            logger.info("User '{}' authenticated from {}", authenticatedUsername, socket.getRemoteSocketAddress());
        } catch (AuthenticationException e) { send(MessageType.S2C_LOGIN_FAILED, new AuthFailedResponse(e.getMessage())); }
    }

    private void handlePrivateMessage(Envelope envelope) throws IOException, ValidationException {
        PrivateMessageRequest req = codec.unwrap(envelope, PrivateMessageRequest.class);
        if (req == null) { sendError("Invalid private message request."); return; }
        PrivateMessageEvent event = chatService.sendPrivateMessage(authenticatedUserId, req.getReceiverId(), req.getMessage());
        ClientHandler recipient = server.getHandler(event.getReceiverId());
        if (recipient != null) {
            chatService.markDelivered(event.getReceiverId(), event.getMessageId()); event.setStatus("DELIVERED");
            recipient.sendAsync(MessageType.S2C_PRIVATE_MESSAGE, event); send(MessageType.S2C_MESSAGE_DELIVERED, event);
        } else send(MessageType.S2C_PRIVATE_MESSAGE, event);
    }

    private void handlePrivateHistory(Envelope envelope) throws IOException, ValidationException {
        PrivateHistoryRequest req = codec.unwrap(envelope, PrivateHistoryRequest.class);
        if (req == null) { sendError("Invalid history request."); return; }
        send(MessageType.S2C_PRIVATE_HISTORY, new PrivateHistoryResponse(req.getOtherUserId(), chatService.history(authenticatedUserId, req.getOtherUserId(), req.getLimit(), req.getBeforeMessageId())));
    }

    private void handleMessageRead(Envelope envelope) throws IOException, ValidationException {
        MessageReadRequest req = codec.unwrap(envelope, MessageReadRequest.class);
        if (req == null) { sendError("Invalid read receipt."); return; }
        if (!chatService.markRead(authenticatedUserId, req.getMessageId())) return;
        OptionalInt senderId = chatService.findMessageSender(authenticatedUserId, req.getMessageId());
        if (senderId.isPresent()) { ClientHandler sender = server.getHandler(senderId.getAsInt()); if (sender != null) sender.sendAsync(MessageType.S2C_MESSAGE_READ, req); }
    }

    private void handleTyping(Envelope envelope, MessageType type) throws IOException {
        PrivateMessageRequest req = codec.unwrap(envelope, PrivateMessageRequest.class);
        if (req == null || req.getReceiverId() <= 0 || req.getReceiverId() == authenticatedUserId || !chatService.userExists(req.getReceiverId())) {
            sendError("Invalid typing recipient."); return;
        }
        ClientHandler recipient = server.getHandler(req.getReceiverId());
        if (recipient != null) recipient.sendAsync(type, new TypingEvent(authenticatedUserId, authenticatedUsername, req.getReceiverId()));
    }

    private void handleCreateGroup(Envelope envelope) throws IOException, ValidationException {
        CreateGroupRequest req = codec.unwrap(envelope, CreateGroupRequest.class);
        if (req == null) { sendError("Invalid group creation request."); return; }
        GroupSummary group = groupService.create(authenticatedUserId, req.getName()); send(MessageType.S2C_GROUP_CREATED, new GroupCreatedResponse(group));
    }
    private void handleJoinGroup(Envelope envelope) throws IOException, ValidationException {
        GroupJoinRequest req = codec.unwrap(envelope, GroupJoinRequest.class);
        if (req == null) { sendError("Invalid group join request."); return; }
        groupService.join(authenticatedUserId, req.getGroupId()); send(MessageType.S2C_GROUP_LIST, new GroupListResponse(groupService.list(authenticatedUserId)));
    }
    private void handleLeaveGroup(Envelope envelope) throws IOException, ValidationException {
        GroupJoinRequest req = codec.unwrap(envelope, GroupJoinRequest.class);
        if (req == null) { sendError("Invalid group leave request."); return; }
        groupService.leave(authenticatedUserId, req.getGroupId()); send(MessageType.S2C_GROUP_LIST, new GroupListResponse(groupService.list(authenticatedUserId)));
    }
    private void handleGroupMessage(Envelope envelope) throws IOException, ValidationException {
        GroupMessageRequest req = codec.unwrap(envelope, GroupMessageRequest.class);
        if (req == null) { sendError("Invalid group message request."); return; }
        GroupMessageEvent event = groupService.sendMessage(authenticatedUserId, authenticatedUsername, req.getGroupId(), req.getMessage());
        for (int memberId : groupService.members(req.getGroupId())) { ClientHandler member = server.getHandler(memberId); if (member != null) member.sendAsync(MessageType.S2C_GROUP_MESSAGE, event); }
    }
    private void handleGroupHistory(Envelope envelope) throws IOException, ValidationException {
        GroupHistoryRequest req = codec.unwrap(envelope, GroupHistoryRequest.class);
        if (req == null) { sendError("Invalid group history request."); return; }
        send(MessageType.S2C_GROUP_HISTORY, new GroupHistoryResponse(req.getGroupId(), groupService.history(authenticatedUserId, req.getGroupId(), req.getLimit(), req.getBeforeMessageId())));
    }

    void send(MessageType type, Object payload) throws IOException { if (out == null) throw new IOException("Client output stream is not initialized."); codec.write(out, codec.wrap(type, payload)); }
    void sendAsync(MessageType type, Object payload) { Thread.startVirtualThread(() -> { try { send(type, payload); } catch (IOException e) { logger.debug("Unable to push {} to user {}", type, authenticatedUserId); } }); }
    private void sendError(String message) { try { send(MessageType.S2C_ERROR, new AuthFailedResponse(message)); } catch (IOException e) { logger.debug("Unable to send error response to {}", socket.getRemoteSocketAddress()); } }
    public int getAuthenticatedUserId() { return authenticatedUserId; }
    public String getUsername() { return authenticatedUsername; }

    private void cleanup() {
        int userId = authenticatedUserId; String token = sessionToken;
        if (userId != -1) { server.deregisterClient(userId, this); authService.logout(token); }
        authenticatedUserId = -1; authenticatedUsername = null; sessionToken = null;
        try { socket.close(); } catch (IOException e) { logger.debug("Error closing socket for {}", socket.getRemoteSocketAddress()); }
    }
    private void handleLogout() { cleanup(); }
}
