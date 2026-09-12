package com.chatapp.service;

import com.chatapp.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatServiceTest {

    private final ChatService service = new ChatService();

    @Test
    void userExistsRejectsNonPositiveIdsWithoutDatabaseAccess() {
        assertFalse(service.userExists(0));
        assertFalse(service.userExists(-1));
    }

    @Test
    void sendPrivateMessageRejectsInvalidRecipients() {
        assertThrows(ValidationException.class,
                () -> service.sendPrivateMessage(7, 0, "hello"));
        assertThrows(ValidationException.class,
                () -> service.sendPrivateMessage(7, -2, "hello"));
        assertThrows(ValidationException.class,
                () -> service.sendPrivateMessage(7, 7, "hello"));
    }

    @Test
    void deliveryReceiptMethodsRejectInvalidMessageIds() {
        assertThrows(ValidationException.class,
                () -> service.markDelivered(7, 0));
        assertThrows(ValidationException.class,
                () -> service.findMessageSender(7, -1));
        assertThrows(ValidationException.class,
                () -> service.markRead(7, 0));
    }
}
