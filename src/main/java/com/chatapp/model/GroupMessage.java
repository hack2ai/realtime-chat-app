package com.chatapp.model;

import java.time.LocalDateTime;

/**
 * Domain model for a message sent to a group, mapping to the
 * {@code group_messages} table.
 */
public class GroupMessage {

    private long id;
    private int groupId;
    private int senderId;
    private String message;
    private LocalDateTime sentAt;

    public GroupMessage() {
        // Required no-arg constructor for Gson and JDBC row mapping.
    }

    public GroupMessage(int groupId, int senderId, String message) {
        this.groupId = groupId;
        this.senderId = senderId;
        this.message = message;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    @Override
    public String toString() {
        return "GroupMessage{id=%d, groupId=%d, sender=%d}".formatted(id, groupId, senderId);
    }
}
