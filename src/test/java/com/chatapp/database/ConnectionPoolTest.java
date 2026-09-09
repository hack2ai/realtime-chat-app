package com.chatapp.database;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPoolTest {

    @Test
    void shutdownClosesIdleAndBorrowedConnections() throws SQLException {
        AtomicInteger created = new AtomicInteger();
        AtomicBoolean firstClosed = new AtomicBoolean();
        AtomicBoolean secondClosed = new AtomicBoolean();

        ConnectionPool pool = new ConnectionPool(1, 2, 1_000, () -> {
            int number = created.incrementAndGet();
            AtomicBoolean closed = number == 1 ? firstClosed : secondClosed;
            return connection(closed);
        });

        Connection borrowed = pool.borrowConnection();
        Connection secondBorrowed = pool.borrowConnection();

        pool.shutdown();

        assertTrue(firstClosed.get(), "Idle connection was not closed during shutdown");
        assertTrue(secondClosed.get(), "Borrowed connection was not closed during shutdown");
        assertThrows(SQLException.class, pool::borrowConnection);

        pool.returnConnection(borrowed);
        pool.returnConnection(secondBorrowed);
        pool.shutdown();
    }

    @Test
    void borrowingPastPoolLimitTimesOutWithoutCreatingExtraConnections() throws SQLException {
        AtomicInteger created = new AtomicInteger();
        ConnectionPool pool = new ConnectionPool(0, 1, 25, () -> {
            created.incrementAndGet();
            return connection(new AtomicBoolean());
        });

        Connection borrowed = pool.borrowConnection();
        SQLException failure = assertThrows(SQLException.class, pool::borrowConnection);

        assertEquals(1, created.get(), "Pool created more connections than its configured maximum");
        assertTrue(failure.getMessage().contains("Timed out after 25ms"));

        pool.returnConnection(borrowed);
        pool.shutdown();
    }

    private static Connection connection(AtomicBoolean closed) {
        return (Connection) Proxy.newProxyInstance(
                ConnectionPoolTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isClosed" -> closed.get();
                    case "isValid" -> !closed.get();
                    case "close" -> {
                        closed.set(true);
                        yield null;
                    }
                    case "toString" -> "test-connection";
                    case "unwrap" -> { throw new SQLException("Not a wrapper"); }
                    case "isWrapperFor" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0.0f;
        if (returnType == double.class) return 0.0d;
        if (returnType == char.class) return '\0';
        return null;
    }
}
