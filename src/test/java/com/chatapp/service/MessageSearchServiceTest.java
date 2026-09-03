package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageSearchServiceTest {

    @Test
    void rejectsUnauthenticatedSearch() {
        MessageSearchService service = new MessageSearchService();
        assertThrows(ValidationException.class, () -> service.searchPrivate(0, "hello", 10));
    }

    @Test
    void rejectsBlankSearch() {
        MessageSearchService service = new MessageSearchService();
        assertThrows(ValidationException.class, () -> service.searchPrivate(1, "   ", 10));
    }

    @Test
    void rejectsOversizedSearchBeforeDatabaseAccess() {
        MessageSearchService service = new MessageSearchService();
        String query = "a".repeat(121);
        assertThrows(ValidationException.class, () -> service.searchPrivate(1, query, 10));
    }
}
