package com.chatapp.service;

import com.chatapp.database.DatabaseManager;
import com.chatapp.exception.ValidationException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Read-only search over messages the authenticated user is allowed to see. */
public class MessageSearchService {
    private static final int MAX_QUERY_LENGTH = 120;
    private static final int MAX_RESULTS = 50;

    public record SearchResult(long messageId, int senderId, String senderUsername,
                               int receiverId, String message, LocalDateTime sentAt) {}

    public List<SearchResult> searchPrivate(int userId, String query, int limit) throws ValidationException {
        if (userId <= 0) throw new ValidationException("You must be logged in to search.");
        if (query == null || query.isBlank()) throw new ValidationException("Search text cannot be empty.");
        String normalized = query.strip();
        if (normalized.length() > MAX_QUERY_LENGTH) throw new ValidationException("Search text is too long.");
        int safeLimit = Math.max(1, Math.min(limit, MAX_RESULTS));
        String sql = "SELECT pm.id, pm.sender_id, su.username AS sender_username, pm.receiver_id, pm.message, pm.sent_at "
                + "FROM private_messages pm JOIN users su ON su.id = pm.sender_id "
                + "WHERE (pm.sender_id = ? OR pm.receiver_id = ?) AND pm.message LIKE ? ESCAPE '\\\\' "
                + "ORDER BY pm.id DESC LIMIT ?";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                stmt.setString(3, "%" + escapeLikePattern(normalized) + "%");
                stmt.setInt(4, safeLimit);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<SearchResult> results = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp sentAt = rs.getTimestamp("sent_at");
                        results.add(new SearchResult(rs.getLong("id"), rs.getInt("sender_id"),
                                rs.getString("sender_username"), rs.getInt("receiver_id"),
                                rs.getString("message"), sentAt == null ? null : sentAt.toLocalDateTime()));
                    }
                    return results;
                }
            }
        });
    }

    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
