package com.chatapp.server;

import com.chatapp.config.AppConfig;
import com.chatapp.database.ConnectionPool;
import com.chatapp.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main server process: binds a listening socket and accepts incoming
 * client connections, handing each one off to a dedicated
 * {@link ClientHandler} running on a pooled thread.
 *
 * <h2>Concurrency model</h2>
 * One thread per connected client (via a bounded thread pool), not a
 * single-threaded event loop. For a project at this scale (a JavaFX
 * desktop chat client, not a web-scale service expecting tens of
 * thousands of concurrent connections), thread-per-client is simpler
 * to reason about and debug than an async/NIO event loop, and the
 * blocking I/O model maps directly onto plain {@link java.net.Socket}
 * + {@link java.io.DataInputStream}/{@link java.io.DataOutputStream},
 * which is what the spec calls for ("Java Socket Programming").
 * {@code server.maxClients} in config bounds the pool size so a flood
 * of connection attempts can't exhaust server threads/memory.
 *
 * <p>This class is intentionally a thin shell in Phase 1: it owns the
 * accept loop and the registry of connected handlers (needed so one
 * client's handler can find another client's handler to deliver a
 * private message — wired up in Phase 2), and delegates all actual
 * protocol logic to {@link ClientHandler}.
 */
public class ChatServer {

    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    private final int port;
    private final ExecutorService clientThreadPool;
    private final AuthenticationService authenticationService;

    /**
     * Registry of currently-connected, authenticated clients, keyed by
     * user ID. Used by Phase 2's message-routing logic to look up "is
     * this recipient currently online, and if so, which handler/socket
     * do I push their message through". A {@link ConcurrentHashMap}
     * since handlers register/deregister themselves concurrently from
     * different threads as clients connect and disconnect.
     */
    private final Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public ChatServer() {
        this.port = AppConfig.getServerPort();
        this.clientThreadPool = Executors.newFixedThreadPool(AppConfig.getServerMaxClients());
        this.authenticationService = new AuthenticationService();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("Chat server started on port {}. Max concurrent clients: {}",
                    port, AppConfig.getServerMaxClients());

            // Ensure the connection pool and any other resources are
            // released cleanly on JVM shutdown (Ctrl+C, kill signal),
            // not just on a graceful stop() call.
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            acceptLoop();

        } catch (IOException e) {
            logger.error("Failed to start server on port {}: {}", port, e.getMessage(), e);
            throw new RuntimeException("Server startup failed", e);
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("New connection from {}", clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, this, authenticationService);
                clientThreadPool.submit(handler);

            } catch (IOException e) {
                if (running) {
                    // Only log as an error if we weren't deliberately
                    // shutting down — a closed ServerSocket during
                    // stop() also throws IOException from accept(),
                    // which is expected, not a failure.
                    logger.error("Error accepting client connection: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Registers a handler as belonging to an authenticated user.
     * Called by {@link ClientHandler} once login succeeds.
     */
    public void registerClient(int userId, ClientHandler handler) {
        connectedClients.put(userId, handler);
        logger.info("User {} registered as online. Total connected: {}", userId, connectedClients.size());
    }

    /**
     * Deregisters a handler, e.g. on disconnect or logout.
     */
    public void deregisterClient(int userId) {
        connectedClients.remove(userId);
        logger.info("User {} deregistered. Total connected: {}", userId, connectedClients.size());
    }

    /** Looks up the handler for a currently-online user, if any. Used for message routing in Phase 2. */
    public ClientHandler getHandler(int userId) {
        return connectedClients.get(userId);
    }

    public boolean isUserOnline(int userId) {
        return connectedClients.containsKey(userId);
    }

    public void stop() {
        if (!running) {
            return; // already stopped; shutdown hook + explicit stop() both calling this is fine
        }
        running = false;
        logger.info("Shutting down chat server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warn("Error closing server socket: {}", e.getMessage());
        }

        clientThreadPool.shutdown();
        ConnectionPool.getInstance().shutdown();
        logger.info("Chat server stopped.");
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }
}
