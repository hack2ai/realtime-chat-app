package com.chatapp.service;

import com.chatapp.database.PrivateMessageDAO;
import com.chatapp.database.UserDAO;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.User;
import com.chatapp.model.dto.ChatDTOs.PrivateMessageEvent;
import com.chatapp.model.dto.ChatDTOs.UserSummary;

import java.util.List;
import java.util.OptionalInt;

/** Business rules for presence and one-to-one chat. */
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final UserDAO userDAO;
    private final PrivateMessageDAO privateMessageDAO;

    public ChatService() { this(new UserDAO(), new PrivateMessageDAO()); }

    public ChatService(UserDAO userDAO, PrivateMessageDAO privateMessageDAO) {
        this.userDAO = userDAO;
        this.privateMessageDAO = privateMessageDAO;
    }

    public List<UserSummary> listUsers(int currentUserId) {
        return userDAO.findAll().stream()
                .filter(user -> user.getId() != currentUserId)
                .map(this::summary)
                .toList();
    }

    public boolean userExists(int userId) {
        return userId > 0 && userDAO.findById(userId).isPresent();
    }

    public PrivateMessageEvent sendPrivateMessage(int senderId, int receiverId, String message)
            throws ValidationException {
        if (receiverId <= 0 || receiverId == senderId) throw new ValidationException("Choose a valid recipient.");
        if (!userExists(receiverId)) throw new ValidationException("Recipient does not exist.");
        if (message == null || message.isBlank()) throw new ValidationException("Message cannot be empty.");
        String normalized = message.strip();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ValidationException("Message exceeds the " + MAX_MESSAGE_LENGTH + " character limit.");
        }
        return privateMessageDAO.insert(senderId, receiverId, normalized);
    }

    public List<PrivateMessageEvent> history(int currentUserId, int otherUserId, int limit, long beforeMessageId)
            throws ValidationException {
        if (!userExists(otherUserId)) throw new ValidationException("User does not exist.");
        return privateMessageDAO.findConversation(currentUserId, otherUserId, limit, beforeMessageId);
    }

    public boolean markDelivered(int receiverId, long messageId) throws ValidationException {
        if (messageId <= 0) throw new ValidationException("Invalid message id.");
        privateMessageDAO.markDelivered(messageId, receiverId);
        return true;
    }

    public OptionalInt findMessageSender(int receiverId, long messageId) throws ValidationException {
        if (messageId <= 0) throw new ValidationException("Invalid message id.");
        return privateMessageDAO.findSenderId(messageId, receiverId);
    }

    public boolean markRead(int receiverId, long messageId) throws ValidationException {
        if (messageId <= 0) throw new ValidationException("Invalid message id.");
        return privateMessageDAO.markRead(messageId, receiverId);
    }

    private UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getRole().name(),
                user.getStatus().name(), user.getLastSeen());
    }
}
