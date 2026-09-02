package com.chatapp.server;

import com.chatapp.config.AppConfig;
import com.chatapp.database.ConnectionPool;
import com.chatapp.model.dto.ChatDTOs.UserPresenceEvent;
import com.chatapp.service.AuthenticationService;
import com.chatapp.service.ChatService;
import com.chatapp.socket.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Main TCP server: accepts connections and dispatches them to client handlers. */
public class ChatServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    private final int port;
    private final int maxClients;
    private final ThreadPoolExecutor clientThreadPool;
    private final AuthenticationService authenticationService;
    private final ChatService chatService;
    private final Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer() {
        this.port = AppConfig.getServerPort();
        this.maxClients = AppConfig.getServerMaxClients();
        this.clientThreadPool = new ThreadPoolExecutor(maxClients, maxClients, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxClients), new ThreadPoolExecutor.AbortPolicy());
        this.authenticationService = new AuthenticationService();
        this.chatService = new ChatService();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("Chat server started on port {} (max active/queued clients: {})", port, maxClients);
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "chat-server-shutdown"));
            acceptLoop();
        } catch (IOException e) {
            logger.error("Failed to start server on port {}", port, e);
            throw new IllegalStateException("Server startup failed", e);
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                configureSocket(clientSocket);
                try {
                    clientThreadPool.execute(new ClientHandler(clientSocket, this, authenticationService, chatService));
                } catch (RejectedExecutionException e) {
                    logger.warn("Rejecting connection from {} because the server is at capacity", clientSocket.getRemoteSocketAddress());
                    closeQuietly(clientSocket);
                }
            } catch (SocketException e) {
                if (running) logger.error("Server socket error while accepting clients", e);
            } catch (IOException e) {
                if (running) logger.error("Error accepting client connection", e);
            }
        }
    }

    private void configureSocket(Socket socket) throws SocketException {
        socket.setTcpNoDelay(true);
        int readTimeout = AppConfig.getSocketReadTimeoutMs();
        if (readTimeout > 0) socket.setSoTimeout(readTimeout);
    }

    /** Registers a handler only when the user is not already connected. */
    public boolean registerClient(int userId, ClientHandler handler) {
        ClientHandler previous = connectedClients.putIfAbsent(userId, handler);
        if (previous != null && previous != handler) {
            logger.warn("Rejected duplicate active connection for user {}", userId);
            return false;
        }
        logger.info("User {} is online. Connected users: {}", userId, connectedClients.size());
        broadcastPresence(userId, handler, true);
        return true;
    }

    /** Removes a handler only if it is still the handler registered for that user. */
    public void deregisterClient(int userId, ClientHandler handler) {
        if (connectedClients.remove(userId, handler)) {
            logger.info("User {} disconnected. Connected users: {}", userId, connectedClients.size());
            broadcastPresence(userId, handler, false);
        }
    }

    private void broadcastPresence(int userId, ClientHandler source, boolean online) {
        UserPresenceEvent event = new UserPresenceEvent(userId, source.getUsername(), online ? "ONLINE" : "OFFLINE");
        MessageType type = online ? MessageType.S2C_USER_ONLINE : MessageType.S2C_USER_OFFLINE;
        for (ClientHandler handler : connectedClients.values()) {
            if (handler != source) handler.sendAsync(type, event);
        }
    }

    public ClientHandler getHandler(int userId) { return connectedClients.get(userId); }
    public boolean isUserOnline(int userId) { return connectedClients.containsKey(userId); }
    public ChatService getChatService() { return chatService; }

    public void stop() {
        if (!running) return;
        running = false;
        logger.info("Shutting down chat server...");
        closeQuietly(serverSocket);
        clientThreadPool.shutdownNow();
        ConnectionPool.getInstance().shutdown();
        logger.info("Chat server stopped.");
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException e) { logger.warn("Error closing server socket", e); }
    }
    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException e) { logger.debug("Error closing rejected client socket", e); }
    }

    public static void main(String[] args) { new ChatServer().start(); }
}
