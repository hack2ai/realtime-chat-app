package com.chatapp.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Wire payloads for presence, private messaging, and typing indicators. */
public final class ChatDTOs {
    private ChatDTOs() {}

    public static class UserSummary {
        private int userId;
        private String username;
        private String role;
        private String status;
        private LocalDateTime lastSeen;
        public UserSummary() {}
        public UserSummary(int userId, String username, String role, String status, LocalDateTime lastSeen) {
            this.userId = userId; this.username = username; this.role = role; this.status = status; this.lastSeen = lastSeen;
        }
        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getStatus() { return status; }
        public LocalDateTime getLastSeen() { return lastSeen; }
        public void setUserId(int v) { userId = v; }
        public void setUsername(String v) { username = v; }
        public void setRole(String v) { role = v; }
        public void setStatus(String v) { status = v; }
        public void setLastSeen(LocalDateTime v) { lastSeen = v; }
    }

    public static class UserListResponse {
        private List<UserSummary> users;
        public UserListResponse() {}
        public UserListResponse(List<UserSummary> users) { this.users = users; }
        public List<UserSummary> getUsers() { return users; }
        public void setUsers(List<UserSummary> users) { this.users = users; }
    }

    public static class UserPresenceEvent {
        private int userId;
        private String username;
        private String status;
        public UserPresenceEvent() {}
        public UserPresenceEvent(int userId, String username, String status) {
            this.userId = userId; this.username = username; this.status = status;
        }
        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getStatus() { return status; }
        public void setUserId(int v) { userId = v; }
        public void setUsername(String v) { username = v; }
        public void setStatus(String v) { status = v; }
    }

    public static class PrivateMessageRequest {
        private int receiverId;
        private String message;
        public PrivateMessageRequest() {}
        public PrivateMessageRequest(int receiverId, String message) { this.receiverId = receiverId; this.message = message; }
        public int getReceiverId() { return receiverId; }
        public String getMessage() { return message; }
        public void setReceiverId(int v) { receiverId = v; }
        public void setMessage(String v) { message = v; }
    }

    public static class PrivateMessageEvent {
        private long messageId;
        private int senderId;
        private int receiverId;
        private String message;
        private String status;
        private LocalDateTime sentAt;
        public PrivateMessageEvent() {}
        public PrivateMessageEvent(long messageId, int senderId, int receiverId, String message, String status, LocalDateTime sentAt) {
            this.messageId = messageId; this.senderId = senderId; this.receiverId = receiverId;
            this.message = message; this.status = status; this.sentAt = sentAt;
        }
        public long getMessageId() { return messageId; }
        public int getSenderId() { return senderId; }
        public int getReceiverId() { return receiverId; }
        public String getMessage() { return message; }
        public String getStatus() { return status; }
        public LocalDateTime getSentAt() { return sentAt; }
        public void setMessageId(long v) { messageId = v; }
        public void setSenderId(int v) { senderId = v; }
        public void setReceiverId(int v) { receiverId = v; }
        public void setMessage(String v) { message = v; }
        public void setStatus(String v) { status = v; }
        public void setSentAt(LocalDateTime v) { sentAt = v; }
    }

    public static class MessageReadRequest {
        private long messageId;
        public MessageReadRequest() {}
        public MessageReadRequest(long messageId) { this.messageId = messageId; }
        public long getMessageId() { return messageId; }
        public void setMessageId(long v) { messageId = v; }
    }

    public static class PrivateHistoryRequest {
        private int otherUserId;
        private int limit;
        private long beforeMessageId;
        public PrivateHistoryRequest() {}
        public PrivateHistoryRequest(int otherUserId, int limit, long beforeMessageId) {
            this.otherUserId = otherUserId; this.limit = limit; this.beforeMessageId = beforeMessageId;
        }
        public int getOtherUserId() { return otherUserId; }
        public int getLimit() { return limit; }
        public long getBeforeMessageId() { return beforeMessageId; }
        public void setOtherUserId(int v) { otherUserId = v; }
        public void setLimit(int v) { limit = v; }
        public void setBeforeMessageId(long v) { beforeMessageId = v; }
    }

    public static class PrivateHistoryResponse {
        private int otherUserId;
        private List<PrivateMessageEvent> messages;
        public PrivateHistoryResponse() {}
        public PrivateHistoryResponse(int otherUserId, List<PrivateMessageEvent> messages) { this.otherUserId = otherUserId; this.messages = messages; }
        public int getOtherUserId() { return otherUserId; }
        public List<PrivateMessageEvent> getMessages() { return messages; }
        public void setOtherUserId(int v) { otherUserId = v; }
        public void setMessages(List<PrivateMessageEvent> v) { messages = v; }
    }

    public static class TypingEvent {
        private int userId;
        private String username;
        private int recipientId;
        public TypingEvent() {}
        public TypingEvent(int userId, String username, int recipientId) {
            this.userId = userId; this.username = username; this.recipientId = recipientId;
        }
        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public int getRecipientId() { return recipientId; }
        public void setUserId(int v) { userId = v; }
        public void setUsername(String v) { username = v; }
        public void setRecipientId(int v) { recipientId = v; }
    }
}
