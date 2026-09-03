package com.chatapp.database;

import com.chatapp.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread-safe bounded JDBC connection pool for the chat server. */
public final class ConnectionPool {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);
    private static volatile ConnectionPool instance;

    private final BlockingQueue<Connection> availableConnections;
    private final Set<Connection> trackedConnections = ConcurrentHashMap.newKeySet();
    private final int maxSize;
    private final int connectionTimeoutMs;
    private final AtomicInteger totalCreated = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    private ConnectionPool() {
        this.maxSize = AppConfig.getDbPoolMaxSize();
        this.connectionTimeoutMs = AppConfig.getDbConnectionTimeoutMs();
        this.availableConnections = new ArrayBlockingQueue<>(maxSize);
        int minIdle = AppConfig.getDbPoolMinIdle();
        for (int i = 0; i < minIdle; i++) availableConnections.offer(createConnection());
        logger.info("Connection pool initialized with {} idle connections (max size {})", minIdle, maxSize);
    }

    public static ConnectionPool getInstance() {
        if (instance == null) {
            synchronized (ConnectionPool.class) {
                if (instance == null) instance = new ConnectionPool();
            }
        }
        return instance;
    }

    private Connection createConnection() {
        if (shutdown.get()) throw new IllegalStateException("Database connection pool is shut down.");
        try {
            Connection conn = DriverManager.getConnection(AppConfig.getJdbcUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
            if (shutdown.get()) {
                closeQuietly(conn);
                throw new IllegalStateException("Database connection pool is shut down.");
            }
            trackedConnections.add(conn);
            totalCreated.incrementAndGet();
            return conn;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create database connection. Check MySQL and database configuration.", e);
        }
    }

    public Connection borrowConnection() throws SQLException {
        if (shutdown.get()) throw new SQLException("Database connection pool is shut down.");

        while (true) {
            Connection conn = availableConnections.poll();
            if (conn == null) {
                if (tryReserveConnection()) return createConnectionSafely();
                try {
                    conn = availableConnections.poll(connectionTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for a database connection.", e);
                }
                if (conn == null) {
                    throw new SQLException("Timed out after " + connectionTimeoutMs + "ms waiting for a database connection.");
                }
            }

            if (isValid(conn)) return conn;
            discardConnection(conn);
            if (shutdown.get()) throw new SQLException("Database connection pool is shut down.");
            // The broken connection released a pool slot. Loop so replacement creation
            // follows the same bounded reservation path as every other new connection.
        }
    }

    private boolean tryReserveConnection() {
        while (true) {
            int current = totalCreated.get();
            if (current >= maxSize) return false;
            if (totalCreated.compareAndSet(current, current + 1)) return true;
        }
    }

    /** Creates a connection for an already-reserved pool slot. */
    private Connection createConnectionSafely() throws SQLException {
        if (shutdown.get()) {
            totalCreated.decrementAndGet();
            throw new SQLException("Database connection pool is shut down.");
        }
        try {
            Connection conn = DriverManager.getConnection(AppConfig.getJdbcUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
            if (shutdown.get()) {
                closeQuietly(conn);
                totalCreated.decrementAndGet();
                throw new SQLException("Database connection pool is shut down.");
            }
            trackedConnections.add(conn);
            return conn;
        } catch (SQLException e) {
            totalCreated.decrementAndGet();
            throw e;
        }
    }

    public void returnConnection(Connection conn) {
        if (conn == null) return;
        if (shutdown.get() || !isValid(conn) || !availableConnections.offer(conn)) {
            discardConnection(conn);
        }
    }

    /** Permanently removes a broken connection from the pool. Safe to call more than once. */
    public void discardConnection(Connection conn) {
        if (conn == null) return;
        if (trackedConnections.remove(conn)) {
            availableConnections.remove(conn);
            closeQuietly(conn);
            totalCreated.updateAndGet(current -> Math.max(0, current - 1));
        } else {
            closeQuietly(conn);
        }
    }

    private boolean isValid(Connection conn) {
        try { return conn != null && !conn.isClosed() && conn.isValid(2); }
        catch (SQLException e) { return false; }
    }

    private void closeQuietly(Connection conn) {
        try { if (conn != null) conn.close(); }
        catch (SQLException e) { logger.warn("Error closing database connection ({}).", e.getClass().getSimpleName()); }
    }

    /** Idempotently closes all currently idle connections. Borrowed connections are rejected after shutdown. */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) return;
        Connection conn;
        while ((conn = availableConnections.poll()) != null) {
            trackedConnections.remove(conn);
            closeQuietly(conn);
            totalCreated.updateAndGet(current -> Math.max(0, current - 1));
        }
        logger.info("Connection pool shut down.");
    }
}
