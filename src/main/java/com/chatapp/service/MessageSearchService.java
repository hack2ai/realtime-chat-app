package com.chatapp.service;

import com.chatapp.database.PrivateMessageDAO;
import com.chatapp.exception.ValidationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only search over messages the authenticated user is allowed to see. */
public class MessageSearchService {
    private static final int MAX_QUERY_LENGTH = 120;
    private static final int MAX_RESULTS = 50;
    private static final RequestRateLimiter SEARCH_RATE_LIMITER =
            new RequestRateLimiter(20, Duration.ofMinutes(1), 10_000);
    private final PrivateMessageDAO privateMessageDAO;

    public MessageSearchService() { this(new PrivateMessageDAO()); }

    public MessageSearchService(PrivateMessageDAO privateMessageDAO) {
        if (privateMessageDAO == null) throw new IllegalArgumentException("Private message DAO must not be null.");
        this.privateMessageDAO = privateMessageDAO;
    }

    public record SearchResult(long messageId, int senderId, String senderUsername,
                               int receiverId, String message, LocalDateTime sentAt) {}

    public List<SearchResult> searchPrivate(int userId, String query, int limit) throws ValidationException {
        if (userId <= 0) throw new ValidationException("You must be logged in to search.");
        if (query == null || query.isBlank()) throw new ValidationException("Search text cannot be empty.");
        String normalized = query.strip();
        if (normalized.length() > MAX_QUERY_LENGTH) throw new ValidationException("Search text is too long.");
        if (!SEARCH_RATE_LIMITER.allow(Integer.toString(userId))) {
            throw new ValidationException("Too many searches. Please try again later.");
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_RESULTS));
        String escapedQuery = escapeLikePattern(normalized);
        return privateMessageDAO.searchConversation(userId, escapedQuery, safeLimit).stream()
                .map(row -> new SearchResult(row.messageId(), row.senderId(), row.senderUsername(),
                        row.receiverId(), row.message(), row.sentAt()))
                .toList();
    }

    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
