package com.chatapp.database;

import com.chatapp.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal, hand-rolled JDBC connection pool.
 *
 * <p>This project intentionally does not pull in HikariCP or a similar
 * library: the spec calls for a JDBC-based project, and for a learning/
 * portfolio context, understanding what a connection pool actually does
 * (and why one is needed at all — raw "new connection per query" does
 * not scale past a handful of concurrent clients because TCP handshake
 * + MySQL auth handshake cost dwarfs most query execution times) is
 * part of the point. For a real production system handling serious
 * load, reaching for HikariCP instead of hand-rolled pooling would be
 * the right call; this implementation is deliberately simple over
 * being maximally robust.
 *
 * <p>Thread-safe: backed by a {@link BlockingQueue}, so multiple
 * {@code ClientHandler} threads can borrow/return connections
 * concurrently without external synchronization.
 */
public final class ConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);

    private static volatile ConnectionPool instance;

    private final BlockingQueue<Connection> availableConnections;
    private final int maxSize;
    private final int connectionTimeoutMs;
    private final AtomicInteger totalCreated = new AtomicInteger(0);

    private ConnectionPool() {
        this.maxSize = AppConfig.getDbPoolMaxSize();
        this.connectionTimeoutMs = AppConfig.getDbConnectionTimeoutMs();
        this.availableConnections = new ArrayBlockingQueue<>(maxSize);

        int minIdle = AppConfig.getDbPoolMinIdle();
        for (int i = 0; i < minIdle; i++) {
            availableConnections.offer(createConnection());
        }
        logger.info("Connection pool initialized with {} idle connections (max size {})", minIdle, maxSize);
    }

    public static ConnectionPool getInstance() {
        if (instance == null) {
            synchronized (ConnectionPool.class) {
                if (instance == null) {
                    instance = new ConnectionPool();
                }
            }
        }
        return instance;
    }

    private Connection createConnection() {
        try {
            Connection conn = DriverManager.getConnection(
                    AppConfig.getJdbcUrl(),
                    AppConfig.getDbUser(),
                    AppConfig.getDbPassword()
            );
            totalCreated.incrementAndGet();
            return conn;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to create database connection. Check that MySQL is running and " +
                    "config.properties has correct db.host/port/name/user/password.", e
            );
        }
    }

    /**
     * Borrows a connection from the pool, creating a new one if the
     * pool is empty but below {@code maxSize}, or blocking up to the
     * configured timeout if the pool is exhausted at max size.
     *
     * <p>Validates the connection isn't dead (e.g. MySQL closed it
     * server-side after an idle timeout) before handing it out, and
     * transparently replaces it if so — callers should never have to
     * handle "connection was silently closed" themselves.
     */
    public Connection borrowConnection() throws SQLException {
        Connection conn = availableConnections.poll();

        if (conn == null) {
            if (totalCreated.get() < maxSize) {
                return createConnection();
            }
            try {
                conn = availableConnections.poll(connectionTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for a database connection.", e);
            }
            if (conn == null) {
                throw new SQLException(
                        "Timed out after " + connectionTimeoutMs +
                        "ms waiting for an available database connection (pool exhausted at max size " + maxSize + ")."
                );
            }
        }

        if (!isValid(conn)) {
            logger.warn("Discarding dead connection from pool; creating a replacement.");
            closeQuietly(conn);
            totalCreated.decrementAndGet();
            return createConnection();
        }

        return conn;
    }

    /**
     * Returns a connection to the pool for reuse. Always call this in
     * a {@code finally} block (or use try-with-resources via
     * {@link PooledConnectionGuard} — see {@code DatabaseManager}) so a
     * connection is never permanently lost from the pool due to an
     * exception path forgetting to return it.
     */
    public void returnConnection(Connection conn) {
        if (conn == null) {
            return;
        }
        if (!availableConnections.offer(conn)) {
            // Pool is somehow already full (shouldn't normally happen
            // since we never hand out more than maxSize connections) —
            // close the extra rather than leak it.
            closeQuietly(conn);
            totalCreated.decrementAndGet();
        }
    }

    private boolean isValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeQuietly(Connection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.warn("Error closing discarded connection: {}", e.getMessage());
        }
    }

    /** Shuts down the pool, closing every pooled connection. Call on server stop. */
    public void shutdown() {
        Connection conn;
        while ((conn = availableConnections.poll()) != null) {
            closeQuietly(conn);
        }
        logger.info("Connection pool shut down.");
    }
}
