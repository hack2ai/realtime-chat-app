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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Thread-safe client transport for the length-prefixed JSON chat protocol.
 * The reader runs independently so unsolicited presence/message events can
 * arrive while the JavaFX application remains responsive.
 */
public final class ChatClientConnection implements AutoCloseable {
    private final MessageCodec codec = new MessageCodec();
    private final Consumer<Envelope> eventListener;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running;
    private Thread readerThread;

    public ChatClientConnection(Consumer<Envelope> eventListener) {
        this.eventListener = eventListener;
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (running) return;
        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(host, port), 5_000);
        newSocket.setTcpNoDelay(true);
        socket = newSocket;
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        running = true;

        readerThread = Thread.ofVirtual().name("chat-client-reader").start(this::readLoop);
    }

    public CompletableFuture<LoginSuccessResponse> login(String usernameOrEmail, String password) {
        CompletableFuture<LoginSuccessResponse> future = new CompletableFuture<>();
        sendAsync(MessageType.C2S_LOGIN, new LoginRequest(usernameOrEmail, password))
                .exceptionally(error -> { future.completeExceptionally(error); return null; });
        awaitResponse(future, MessageType.S2C_LOGIN_SUCCESS, MessageType.S2C_LOGIN_FAILED);
        return future;
    }

    public CompletableFuture<RegisterSuccessResponse> register(String username, String email, String password, String confirmPassword) {
        CompletableFuture<RegisterSuccessResponse> future = new CompletableFuture<>();
        sendAsync(MessageType.C2S_REGISTER, new RegisterRequest(username, email, password, confirmPassword))
                .exceptionally(error -> { future.completeExceptionally(error); return null; });
        awaitResponse(future, MessageType.S2C_REGISTER_SUCCESS, MessageType.S2C_REGISTER_FAILED);
        return future;
    }

    private <T> void awaitResponse(CompletableFuture<T> future, MessageType success, MessageType failure) {
        Consumer<Envelope> previous = event -> {
            if (event.getType() == success) {
                future.complete(codec.unwrap(event, success == MessageType.S2C_LOGIN_SUCCESS
                        ? com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse.class
                        : com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse.class));
            } else if (event.getType() == failure) {
                AuthFailedResponse error = codec.unwrap(event, AuthFailedResponse.class);
                future.completeExceptionally(new IllegalStateException(error == null ? "Request failed." : error.getReason()));
            } else {
                eventListener.accept(event);
            }
        };
        CompletableFuture.runAsync(() -> {
            // The transport has one reader; temporarily route the next auth response.
            synchronized (authHandlers) { authHandlers.add(previous); }
        });
    }

    private final java.util.List<Consumer<Envelope>> authHandlers = new java.util.ArrayList<>();

    private void readLoop() {
        try {
            while (running) {
                Envelope envelope = codec.read(in);
                boolean handled = false;
                synchronized (authHandlers) {
                    if (!authHandlers.isEmpty() && (envelope.getType() == MessageType.S2C_LOGIN_SUCCESS
                            || envelope.getType() == MessageType.S2C_LOGIN_FAILED
                            || envelope.getType() == MessageType.S2C_REGISTER_SUCCESS
                            || envelope.getType() == MessageType.S2C_REGISTER_FAILED)) {
                        Consumer<Envelope> handler = authHandlers.remove(0);
                        handler.accept(envelope);
                        handled = true;
                    }
                }
                if (!handled) eventListener.accept(envelope);
            }
        } catch (IOException e) {
            if (running) eventListener.accept(codec.wrap(MessageType.S2C_ERROR,
                    new AuthFailedResponse("Connection lost: " + e.getMessage())));
        } finally {
            running = false;
        }
    }

    public void send(MessageType type, Object payload) throws IOException {
        synchronized (this) {
            if (!running || out == null) throw new IOException("Not connected.");
            codec.write(out, codec.wrap(type, payload));
        }
    }

    public CompletableFuture<Void> sendAsync(MessageType type, Object payload) {
        return CompletableFuture.runAsync(() -> {
            try {
                send(type, payload);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public boolean isConnected() { return running; }

    public synchronized void close() {
        running = false;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
