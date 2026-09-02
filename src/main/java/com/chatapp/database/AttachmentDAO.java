package com.chatapp.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence for private attachment metadata; never stores file bytes. */
public final class AttachmentDAO {
    public record AttachmentRecord(String id, int senderId, int receiverId, String fileName,
                                    String contentType, long sizeBytes, String sha256, LocalDateTime createdAt) {}

    public void insert(AttachmentRecord file) {
        String sql = "INSERT INTO private_attachments "
                + "(id,sender_id,receiver_id,file_name,content_type,size_bytes,sha256) VALUES (?,?,?,?,?,?,?)";
        DatabaseManager.executeVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1,file.id()); stmt.setInt(2,file.senderId()); stmt.setInt(3,file.receiverId());
                stmt.setString(4,file.fileName()); stmt.setString(5,file.contentType()); stmt.setLong(6,file.sizeBytes()); stmt.setString(7,file.sha256());
                stmt.executeUpdate();
            }
        });
    }

    /** Returns metadata only when the authenticated user is a participant. */
    public Optional<AttachmentRecord> findForUser(String id, int userId) {
        String sql = "SELECT id,sender_id,receiver_id,file_name,content_type,size_bytes,sha256,created_at "
                + "FROM private_attachments WHERE id=? AND (sender_id=? OR receiver_id=?)";
        return DatabaseManager.execute(conn -> {
            try (PreparedStatement stmt=conn.prepareStatement(sql)) {
                stmt.setString(1,id); stmt.setInt(2,userId); stmt.setInt(3,userId);
                try (ResultSet rs=stmt.executeQuery()) {
                    if(!rs.next()) return Optional.empty();
                    Timestamp at=rs.getTimestamp("created_at");
                    return Optional.of(new AttachmentRecord(rs.getString("id"),rs.getInt("sender_id"),rs.getInt("receiver_id"),
                            rs.getString("file_name"),rs.getString("content_type"),rs.getLong("size_bytes"),rs.getString("sha256"),
                            at==null?null:at.toLocalDateTime()));
                }
            }
        });
    }
}
