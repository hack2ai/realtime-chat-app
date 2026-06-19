package com.chatapp.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Facade over {@link ConnectionPool} that DAOs use to execute database
 * operations without each DAO having to manually manage
 * borrow/return/exception-wrapping boilerplate.
 *
 * <p>The {@link #execute} method guarantees the borrowed connection is
 * always returned to the pool exactly once, even if the operation
 * throws — this is the single place that pattern is implemented, so
 * individual DAO methods can't forget a {@code finally} block and leak
 * a connection out of the pool.
 */
public final class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private DatabaseManager() {
    }

    /**
     * Functional interface for a unit of work that needs a {@link Connection}
     * and may throw {@link SQLException}. Distinct from
     * {@link java.util.function.Function} because {@code Function}
     * doesn't allow checked exceptions in its signature, and forcing
     * every DAO method to wrap SQLException in a try/catch just to
     * satisfy the functional interface would defeat the point of this
     * abstraction.
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T apply(Connection conn) throws SQLException;
    }

    /**
     * Borrows a connection, runs {@code operation} with it, and
     * guarantees the connection is returned to the pool afterward
     * regardless of success or failure.
     *
     * @throws RuntimeException wrapping the original {@link SQLException},
     *         so callers further up the stack (service layer) aren't
     *         forced to declare {@code throws SQLException} on every
     *         method just to satisfy the compiler — database failures
     *         at this layer are treated as unexpected/exceptional,
     *         distinct from expected validation/auth failures which use
     *         checked exceptions (see {@code exception} package).
     */
    public static <T> T execute(SqlOperation<T> operation) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getInstance().borrowConnection();
            return operation.apply(conn);
        } catch (SQLException e) {
            logger.error("Database operation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Database operation failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                ConnectionPool.getInstance().returnConnection(conn);
            }
        }
    }

    /**
     * Variant of {@link #execute} for operations that don't return a
     * value (e.g. a bare UPDATE/DELETE where the caller only cares
     * about success/failure, not a result).
     */
    public static void executeVoid(VoidSqlOperation operation) {
        execute(conn -> {
            operation.apply(conn);
            return null;
        });
    }

    @FunctionalInterface
    public interface VoidSqlOperation {
        void apply(Connection conn) throws SQLException;
    }
}
