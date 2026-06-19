package com.chatapp.database;

import com.chatapp.model.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access object for the {@code users} table.
 *
 * <p><b>Security note:</b> every query in this class uses
 * {@link PreparedStatement} parameter placeholders ({@code ?}) for all
 * user-supplied values. None of these methods ever build SQL via
 * string concatenation of caller-provided data — that would open this
 * class up to SQL injection (e.g. a username of
 * {@code "'; DROP TABLE users; --"} would be catastrophic if
 * concatenated directly into a query string). This rule is non-negotiable
 * and applies to every DAO in this codebase, not just this one.
 */
public class UserDAO {

    /**
     * Inserts a new user and returns the persisted entity with its
     * generated ID populated.
     *
     * @throws RuntimeException (via {@link DatabaseManager}) if the
     *         insert violates the unique constraint on username or
     *         email — callers (the service layer) should check
     *         {@link #usernameExists} / {@link #emailExists} *before*
     *         calling this to produce a friendly validation error
     *         instead of relying on catching a raw constraint violation.
     */
    public User insert(User user) {
        String sql = """
                INSERT INTO users (username, email, password_hash, role, status)
                VALUES (?, ?, ?, ?, ?)
                """;

        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, user.getEmail());
                stmt.setString(3, user.getPasswordHash());
                stmt.setString(4, user.getRole().name());
                stmt.setString(5, user.getStatus().name());
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setId(keys.getInt(1));
                    }
                }
                return user;
            }
        });
    }

    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    /**
     * Looks up a user by either username or email in a single query,
     * matching the login form's "Username/Email" combined field.
     */
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT * FROM users WHERE username = ? OR email = ?";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, usernameOrEmail);
                stmt.setString(2, usernameOrEmail);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public boolean usernameExists(String username) {
        return findByUsername(username).isPresent();
    }

    public boolean emailExists(String email) {
        return findByEmail(email).isPresent();
    }

    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY username ASC";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
                return users;
            }
        });
    }

    public List<User> findByStatus(User.Status status) {
        String sql = "SELECT * FROM users WHERE status = ? ORDER BY username ASC";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    List<User> users = new ArrayList<>();
                    while (rs.next()) {
                        users.add(mapRow(rs));
                    }
                    return users;
                }
            }
        });
    }

    public void updateStatus(int userId, User.Status status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        DatabaseManager.executeVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        });
    }

    public void updateLastSeen(int userId, java.time.LocalDateTime lastSeen) {
        String sql = "UPDATE users SET last_seen = ? WHERE id = ?";
        DatabaseManager.executeVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.valueOf(lastSeen));
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        });
    }

    public void updatePasswordHash(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        DatabaseManager.executeVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newPasswordHash);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        });
    }

    public void updateProfileImage(int userId, String imagePath) {
        String sql = "UPDATE users SET profile_image = ? WHERE id = ?";
        DatabaseManager.executeVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, imagePath);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Maps the current row of a {@link ResultSet} (positioned by a
     * prior {@code rs.next()}) to a {@link User} domain object.
     */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setProfileImage(rs.getString("profile_image"));
        user.setRole(User.Role.valueOf(rs.getString("role")));
        user.setStatus(User.Status.valueOf(rs.getString("status")));

        Timestamp lastSeen = rs.getTimestamp("last_seen");
        if (lastSeen != null) {
            user.setLastSeen(lastSeen.toLocalDateTime());
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        return user;
    }
}
