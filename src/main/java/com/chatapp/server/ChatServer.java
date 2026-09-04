package com.chatapp.server;

import com.chatapp.config.AppConfig;
import com.chatapp.database.ConnectionPool;
import com.chatapp.security.TlsContextFactory;
import com.chatapp.service.AuthenticationService;
import com.chatapp.service.GroupService;
import com.chatapp.service.ChatService;
import com.chatapp.service.RequestRateLimiter;
import com.chatapp.socket.protocol.MessageType;
import com.chatapp.model.dto.ChatDTOs.UserPresenceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Main TCP chat server and connection registry. */
public class ChatServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    private static final RequestRateLimiter CONNECTION_RATE_LIMITER = new RequestRateLimiter(30, Duration.ofSeconds(10), 10_000);
    private static final ReadinessMarker READINESS_MARKER = new ReadinessMarker(
            Path.of(System.getProperty("java.io.tmpdir"), "chatapp.ready"));
    private static final long METRICS_INTERVAL_SECONDS = 60L;

    private final AuthenticationService authService;
    private final ChatService chatService = new ChatService();
    private final GroupService groupService = new GroupService();
    private final ConcurrentHashMap<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final Set<ClientHandler> activeHandlers = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor clientThreadPool;
    private final ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("chat-server-metrics-").factory());
    private final Thread shutdownHook = new Thread(this::stop, "chat-server-shutdown");
    private volatile boolean shutdownHookRegistered;
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
        verifyDatabaseReady();
        String bindAddress = AppConfig.getServerBindAddress();
        int port = AppConfig.getServerPort();
        InetAddress address = InetAddress.getByName(bindAddress);
        serverSocket = createServerSocket(address, port);
        try {
            running = true;
            READINESS_MARKER.markReady();
            registerShutdownHook();
            scheduleRuntimeMetrics();
            logger.info("Chat server listening on {}:{} (TLS: {})", bindAddress, port, AppConfig.isTlsEnabled());
            acceptLoop();
        } catch (IOException | RuntimeException e) {
            running = false;
            READINESS_MARKER.clear();
            metricsScheduler.shutdownNow();
            closeQuietly(serverSocket);
            serverSocket = null;
            throw e;
        }
    }

    private void scheduleRuntimeMetrics() {
        metricsScheduler.scheduleAtFixedRate(this::logRuntimeMetrics,
                METRICS_INTERVAL_SECONDS, METRICS_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void logRuntimeMetrics() {
        if (!running) return;
        logger.info("Server metrics: connectedUsers={}, activeHandlers={}, poolActive={}, poolSize={}, queueDepth={}, completedHandlers={}",
                connectedClients.size(),
                activeHandlers.size(),
                clientThreadPool.getActiveCount(),
                clientThreadPool.getPoolSize(),
                clientThreadPool.getQueue().size(),
                clientThreadPool.getCompletedTaskCount());
    }

    private void verifyDatabaseReady() throws IOException {
        Connection connection = null;
        try {
            connection = ConnectionPool.getInstance().borrowConnection();
            if (!connection.isValid(2)) throw new SQLException("Database connection validation failed.");
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Database is not ready.", e);
        } finally {
            if (connection != null) ConnectionPool.getInstance().returnConnection(connection);
        }
    }

    private ServerSocket createServerSocket(InetAddress address, int port) throws IOException {
        if (!AppConfig.isTlsEnabled()) return new ServerSocket(port, 50, address);
        try {
            SSLServerSocketFactory factory = TlsContextFactory.createServerContext().getServerSocketFactory();
            SSLServerSocket sslSocket = (SSLServerSocket) factory.createServerSocket(port, 50, address);
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            return sslSocket;
        } catch (IllegalStateException e) {
            throw new IOException("TLS server initialization failed.", e);
        }
    }

    private synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) return;
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            shutdownHookRegistered = true;
        } catch (IllegalStateException e) {
            logger.debug("JVM shutdown is already in progress; shutdown hook was not registered");
        }
    }

    private synchronized void unregisterShutdownHook() {
        if (!shutdownHookRegistered) return;
        try {
            if (Runtime.getRuntime().removeShutdownHook(shutdownHook)) shutdownHookRegistered = false;
        } catch (IllegalStateException e) {
            // The JVM is already shutting down; the hook is executing or will execute.
        }
    }

    private void acceptLoop() {
        while (running) {
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
                configureSocket(clientSocket);
                if (!allowConnection(clientSocket)) {
                    logger.warn("Rejecting connection from {} because connection rate limit was exceeded", clientSocket.getRemoteSocketAddress());
                    closeQuietly(clientSocket);
                    clientSocket = null;
                    continue;
                }
                ClientHandler handler = new ClientHandler(clientSocket, this, authService, chatService, groupService);
                activeHandlers.add(handler);
                try {
                    clientThreadPool.execute(handler);
                    clientSocket = null;
                } catch (RejectedExecutionException e) {
                    activeHandlers.remove(handler);
                    logger.warn("Rejecting connection from {} because the server is at capacity", clientSocket.getRemoteSocketAddress());
                    closeQuietly(clientSocket);
                    clientSocket = null;
                }
            } catch (SocketException e) {
                closeQuietly(clientSocket);
                if (running) logger.error("Server socket error while accepting clients", e);
            } catch (IOException e) {
                closeQuietly(clientSocket);
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
        if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        }
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

    void handlerClosed(ClientHandler handler) { activeHandlers.remove(handler); }

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
        if (!running) {
            READINESS_MARKER.clear();
            metricsScheduler.shutdownNow();
            return;
        }
        running = false;
        READINESS_MARKER.clear();
        metricsScheduler.shutdownNow();
        unregisterShutdownHook();
        logger.info("Shutting down chat server ({} active connections)...", activeHandlers.size());
        closeQuietly(serverSocket);
        serverSocket = null;
        for (ClientHandler handler : activeHandlers.toArray(ClientHandler[]::new)) handler.closeConnection();
        clientThreadPool.shutdownNow();
        try {
            if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) logger.warn("Client handler pool did not terminate within 5 seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ConnectionPool.getInstance().shutdown();
        logger.info("Chat server stopped.");
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) {}
    }
}
