package com.chatapp.model;

import java.time.LocalDateTime;

/**
 * Domain model for a one-to-one private message, mapping to the
 * {@code private_messages} table.
 */
public class PrivateMessage {

    public enum MessageStatus { SENT, DELIVERED, READ }

    private long id;
    private int senderId;
    private int receiverId;
    private String message;
    private MessageStatus status;
    private LocalDateTime sentAt;

    public PrivateMessage() {
        // Required no-arg constructor for Gson and JDBC row mapping.
    }

    public PrivateMessage(int senderId, int receiverId, String message) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.message = message;
        this.status = MessageStatus.SENT;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public int getReceiverId() { return receiverId; }
    public void setReceiverId(int receiverId) { this.receiverId = receiverId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    @Override
    public String toString() {
        return "PrivateMessage{id=%d, sender=%d, receiver=%d, status=%s}"
                .formatted(id, senderId, receiverId, status);
    }
}
