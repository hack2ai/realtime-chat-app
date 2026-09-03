package com.chatapp.server;

import com.chatapp.config.AppConfig;
import com.chatapp.database.ConnectionPool;
import com.chatapp.service.AuthenticationService;
import com.chatapp.service.GroupService;
import com.chatapp.service.ChatService;
import com.chatapp.service.RequestRateLimiter;
import com.chatapp.socket.protocol.MessageType;
import com.chatapp.model.dto.ChatDTOs.UserPresenceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

/** Main TCP chat server and connection registry. */
public class ChatServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    private static final RequestRateLimiter CONNECTION_RATE_LIMITER = new RequestRateLimiter(30, Duration.ofSeconds(10), 10_000);

    private final AuthenticationService authService;
    private final ChatService chatService = new ChatService();
    private final GroupService groupService = new GroupService();
    private final ConcurrentHashMap<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final Set<ClientHandler> activeHandlers = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor clientThreadPool;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public ChatServer(AuthenticationService authService) {
        this.authService = authService;
        int maxClients = AppConfig.getServerMaxClients();
        int queueCapacity = Math.max(1, maxClients / 2);
        this.clientThreadPool = new ThreadPoolExecutor(
                Math.min(4, maxClients), maxClients,
                30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual().factory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.clientThreadPool.allowCoreThreadTimeOut(true);
    }

    public void start() throws IOException {
        if (running) return;
        String bindAddress = AppConfig.getServerBindAddress();
        int port = AppConfig.getServerPort();
        InetAddress address = InetAddress.getByName(bindAddress);
        serverSocket = new ServerSocket(port, 50, address);
        running = true;
        logger.info("Chat server listening on {}:{}", bindAddress, port);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "chat-server-shutdown"));
        acceptLoop();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                configureSocket(clientSocket);
                if (!allowConnection(clientSocket)) {
                    logger.warn("Rejecting connection from {} because connection rate limit was exceeded", clientSocket.getRemoteSocketAddress());
                    closeQuietly(clientSocket);
                    continue;
                }
                ClientHandler handler = new ClientHandler(clientSocket, this, authService, chatService, groupService);
                activeHandlers.add(handler);
                try {
                    clientThreadPool.execute(handler);
                } catch (RejectedExecutionException e) {
                    activeHandlers.remove(handler);
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

    private boolean allowConnection(Socket socket) {
        InetAddress address = socket.getInetAddress();
        String key = address == null ? "ip:unknown" : "ip:" + address.getHostAddress();
        return CONNECTION_RATE_LIMITER.allow(key);
    }

    private void configureSocket(Socket socket) throws SocketException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        int readTimeout = AppConfig.getSocketReadTimeoutMs();
        if (readTimeout > 0) socket.setSoTimeout(readTimeout);
    }

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

    public void deregisterClient(int userId, ClientHandler handler) {
        if (connectedClients.remove(userId, handler)) {
            logger.info("User {} disconnected. Connected users: {}", userId, connectedClients.size());
            broadcastPresence(userId, handler, false);
        }
    }

    void handlerClosed(ClientHandler handler) {
        activeHandlers.remove(handler);
    }

    private void broadcastPresence(int userId, ClientHandler source, boolean online) {
        UserPresenceEvent event = new UserPresenceEvent(userId, source.getUsername(), online ? "ONLINE" : "OFFLINE");
        MessageType type = online ? MessageType.S2C_USER_ONLINE : MessageType.S2C_USER_OFFLINE;
        for (ClientHandler handler : connectedClients.values()) {
            if (handler != source) handler.sendAsync(type, event);
        }
    }

    public Collection<ClientHandler> connectedHandlers() { return connectedClients.values(); }
    public ClientHandler getHandler(int userId) { return connectedClients.get(userId); }
    public boolean isUserOnline(int userId) { return connectedClients.containsKey(userId); }
    public ChatService getChatService() { return chatService; }
    public GroupService getGroupService() { return groupService; }

    public void stop() {
        if (!running) return;
        running = false;
        logger.info("Shutting down chat server ({} active connections)...", activeHandlers.size());
        closeQuietly(serverSocket);
        for (ClientHandler handler : activeHandlers.toArray(ClientHandler[]::new)) {
            handler.closeConnection();
        }
        clientThreadPool.shutdownNow();
        try {
            if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Client handler pool did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ConnectionPool.getInstance().shutdown();
        logger.info("Chat server stopped.");
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
