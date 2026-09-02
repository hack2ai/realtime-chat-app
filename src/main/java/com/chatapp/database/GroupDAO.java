package com.chatapp.database;

import com.chatapp.model.dto.GroupDTOs.GroupMessageEvent;
import com.chatapp.model.dto.GroupDTOs.GroupSummary;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persistence operations for groups, membership, and group messages. */
public class GroupDAO {
    public GroupSummary create(int ownerId, String name) {
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO chat_groups (group_name, created_by) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, name); stmt.setInt(2, ownerId); stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Database did not return a group id.");
                    int id = keys.getInt(1);
                    try (PreparedStatement member = conn.prepareStatement("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'ADMIN')")) {
                        member.setInt(1, id); member.setInt(2, ownerId); member.executeUpdate();
                    }
                    return new GroupSummary(id, name, ownerId, 1);
                }
            }
        });
    }
    public boolean exists(int groupId) { return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM chat_groups WHERE id = ?")) { stmt.setInt(1, groupId); try (ResultSet rs = stmt.executeQuery()) { return rs.next(); } } }); }
    public boolean isMember(int groupId, int userId) { return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?")) { stmt.setInt(1, groupId); stmt.setInt(2, userId); try (ResultSet rs = stmt.executeQuery()) { return rs.next(); } } }); }
    public boolean addMember(int groupId, int userId) { return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement("INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)")) { stmt.setInt(1, groupId); stmt.setInt(2, userId); return stmt.executeUpdate() > 0; } }); }
    public boolean removeMember(int groupId, int userId) { return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM group_members WHERE group_id = ? AND user_id = ?")) { stmt.setInt(1, groupId); stmt.setInt(2, userId); return stmt.executeUpdate() > 0; } }); }
    public List<Integer> memberIds(int groupId) { return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM group_members WHERE group_id = ? ORDER BY user_id")) { stmt.setInt(1, groupId); try (ResultSet rs = stmt.executeQuery()) { List<Integer> ids = new ArrayList<>(); while (rs.next()) ids.add(rs.getInt(1)); return ids; } } }); }
    public List<GroupSummary> findForUser(int userId) {
        String sql = "SELECT g.id, g.group_name, g.created_by, COUNT(gm2.user_id) member_count FROM chat_groups g JOIN group_members gm ON gm.group_id = g.id JOIN group_members gm2 ON gm2.group_id = g.id WHERE gm.user_id = ? GROUP BY g.id, g.group_name, g.created_by ORDER BY g.group_name";
        return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, userId); try (ResultSet rs = stmt.executeQuery()) { List<GroupSummary> groups = new ArrayList<>(); while (rs.next()) groups.add(new GroupSummary(rs.getInt("id"), rs.getString("group_name"), rs.getInt("created_by"), rs.getInt("member_count"))); return groups; } } });
    }
    public GroupMessageEvent insertMessage(int groupId, int senderId, String senderUsername, String message) {
        return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO group_messages (group_id, sender_id, message) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) { stmt.setInt(1, groupId); stmt.setInt(2, senderId); stmt.setString(3, message); stmt.executeUpdate(); try (ResultSet keys = stmt.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Database did not return a message id."); return new GroupMessageEvent(keys.getLong(1), groupId, senderId, senderUsername, message, LocalDateTime.now()); } } });
    }
    public List<GroupMessageEvent> history(int groupId, int limit, long beforeId) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = beforeId > 0 ? "SELECT gm.id, gm.group_id, gm.sender_id, u.username, gm.message, gm.sent_at FROM group_messages gm JOIN users u ON u.id = gm.sender_id WHERE gm.group_id = ? AND gm.id < ? ORDER BY gm.id DESC LIMIT ?" : "SELECT gm.id, gm.group_id, gm.sender_id, u.username, gm.message, gm.sent_at FROM group_messages gm JOIN users u ON u.id = gm.sender_id WHERE gm.group_id = ? ORDER BY gm.id DESC LIMIT ?";
        return DatabaseManager.execute(conn -> { try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, groupId); if (beforeId > 0) { stmt.setLong(2, beforeId); stmt.setInt(3, safeLimit); } else stmt.setInt(2, safeLimit); try (ResultSet rs = stmt.executeQuery()) { List<GroupMessageEvent> result = new ArrayList<>(); while (rs.next()) result.add(mapMessage(rs)); Collections.reverse(result); return result; } } });
    }
    private GroupMessageEvent mapMessage(ResultSet rs) throws SQLException { Timestamp sentAt = rs.getTimestamp("sent_at"); return new GroupMessageEvent(rs.getLong("id"), rs.getInt("group_id"), rs.getInt("sender_id"), rs.getString("username"), rs.getString("message"), sentAt == null ? null : sentAt.toLocalDateTime()); }
}
