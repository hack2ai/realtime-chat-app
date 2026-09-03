package com.chatapp.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Facade over {@link ConnectionPool} that centralizes JDBC connection
 * lifecycle, exception handling, and transaction boundaries for DAOs.
 */
public final class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private DatabaseManager() {
    }

    @FunctionalInterface
    public interface SqlOperation<T> {
        T apply(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    public interface VoidSqlOperation {
        void apply(Connection conn) throws SQLException;
    }

    /** Borrows a connection, executes the operation, and always returns it. */
    public static <T> T execute(SqlOperation<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("SQL operation must not be null.");
        }

        Connection conn = null;
        boolean reusable = true;
        try {
            conn = ConnectionPool.getInstance().borrowConnection();
            return operation.apply(conn);
        } catch (SQLException e) {
            logger.error("Database operation failed: {}", e.getMessage(), e);
            reusable = false;
            throw new RuntimeException("Database operation failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Database operation failed unexpectedly: {}", e.getMessage(), e);
            reusable = false;
            throw e;
        } finally {
            if (conn != null) {
                if (reusable) {
                    ConnectionPool.getInstance().returnConnection(conn);
                } else {
                    ConnectionPool.getInstance().discardConnection(conn);
                }
            }
        }
    }

    /** Executes a database operation that does not return a value. */
    public static void executeVoid(VoidSqlOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("SQL operation must not be null.");
        }
        execute(conn -> {
            operation.apply(conn);
            return null;
        });
    }

    /**
     * Executes a unit of work in a single transaction. A connection whose
     * transaction state cannot be restored is discarded rather than returned
     * to the pool, preventing transaction-state leakage between callers.
     */
    public static <T> T executeTransaction(SqlOperation<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("SQL transaction must not be null.");
        }

        Connection conn = null;
        boolean reusable = true;
        try {
            conn = ConnectionPool.getInstance().borrowConnection();
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                T result = operation.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                    reusable = false;
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException restoreError) {
                    reusable = false;
                    logger.warn("Failed to restore JDBC auto-commit state; discarding connection: {}",
                            restoreError.getMessage());
                }
            }
        } catch (SQLException e) {
            logger.error("Database transaction failed: {}", e.getMessage(), e);
            throw new RuntimeException("Database transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                if (reusable) {
                    ConnectionPool.getInstance().returnConnection(conn);
                } else {
                    ConnectionPool.getInstance().discardConnection(conn);
                }
            }
        }
    }
}
