package com.chatapp.database;

import com.chatapp.model.dto.ChatDTOs.PrivateMessageEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/** Persistence operations for one-to-one messages. */
public class PrivateMessageDAO {

    public record SearchRecord(long messageId, int senderId, String senderUsername,
                               int receiverId, String message, LocalDateTime sentAt) {}

    public PrivateMessageEvent insert(int senderId, int receiverId, String message) {
        String sql = "INSERT INTO private_messages (sender_id, receiver_id, message) VALUES (?, ?, ?)";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);
                stmt.setString(3, message);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Database did not return a message id.");
                    long id = keys.getLong(1);
                    return new PrivateMessageEvent(id, senderId, receiverId, message, "SENT", LocalDateTime.now());
                }
            }
        });
    }

    public List<PrivateMessageEvent> findConversation(int userA, int userB, int limit, long beforeMessageId) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = beforeMessageId > 0
                ? "SELECT id, sender_id, receiver_id, message, msg_status, sent_at FROM private_messages "
                  + "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) "
                  + "AND id < ? ORDER BY id DESC LIMIT ?"
                : "SELECT id, sender_id, receiver_id, message, msg_status, sent_at FROM private_messages "
                  + "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) "
                  + "ORDER BY id DESC LIMIT ?";

        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userA);
                stmt.setInt(2, userB);
                stmt.setInt(3, userB);
                stmt.setInt(4, userA);
                if (beforeMessageId > 0) {
                    stmt.setLong(5, beforeMessageId);
                    stmt.setInt(6, safeLimit);
                } else {
                    stmt.setInt(5, safeLimit);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PrivateMessageEvent> messages = new ArrayList<>();
                    while (rs.next()) messages.add(map(rs));
                    Collections.reverse(messages);
                    return messages;
                }
            }
        });
    }

    public List<SearchRecord> searchConversation(int userId, String escapedQuery, int limit) {
        String sql = "SELECT pm.id, pm.sender_id, su.username AS sender_username, pm.receiver_id, pm.message, pm.sent_at "
                + "FROM private_messages pm JOIN users su ON su.id = pm.sender_id "
                + "WHERE (pm.sender_id = ? OR pm.receiver_id = ?) AND pm.message LIKE ? ESCAPE '\\\\' "
                + "ORDER BY pm.id DESC LIMIT ?";
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                stmt.setString(3, "%" + escapedQuery + "%");
                stmt.setInt(4, safeLimit);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<SearchRecord> results = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp sentAt = rs.getTimestamp("sent_at");
                        results.add(new SearchRecord(rs.getLong("id"), rs.getInt("sender_id"),
                                rs.getString("sender_username"), rs.getInt("receiver_id"),
                                rs.getString("message"), sentAt == null ? null : sentAt.toLocalDateTime()));
                    }
                    return results;
                }
            }
        });
    }

    /** Marks a message read only when the requesting user is its receiver. */
    public boolean markRead(long messageId, int receiverId) {
        String sql = "UPDATE private_messages SET msg_status = 'READ' "
                + "WHERE id = ? AND receiver_id = ? AND msg_status <> 'READ'";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, messageId);
                stmt.setInt(2, receiverId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    public void markDelivered(long messageId, int receiverId) {
        String sql = "UPDATE private_messages SET msg_status = 'DELIVERED' "
                + "WHERE id = ? AND receiver_id = ? AND msg_status = 'SENT'";
        DatabaseManager.executeVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, messageId);
                stmt.setInt(2, receiverId);
                stmt.executeUpdate();
            }
        });
    }

    /** Returns the sender id only when the requesting user is the message receiver. */
    public OptionalInt findSenderId(long messageId, int receiverId) {
        String sql = "SELECT sender_id FROM private_messages WHERE id = ? AND receiver_id = ?";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, messageId);
                stmt.setInt(2, receiverId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? OptionalInt.of(rs.getInt("sender_id")) : OptionalInt.empty();
                }
            }
        });
    }

    private PrivateMessageEvent map(ResultSet rs) throws SQLException {
        Timestamp sentAt = rs.getTimestamp("sent_at");
        return new PrivateMessageEvent(
                rs.getLong("id"),
                rs.getInt("sender_id"),
                rs.getInt("receiver_id"),
                rs.getString("message"),
                rs.getString("msg_status"),
                sentAt == null ? null : sentAt.toLocalDateTime()
        );
    }
}
