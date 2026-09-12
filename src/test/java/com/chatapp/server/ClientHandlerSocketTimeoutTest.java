package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.Socket;
import org.junit.jupiter.api.Test;

class ClientHandlerSocketTimeoutTest {
    @Test
    void authenticatedSessionClearsHandshakeReadTimeout() throws Exception {
        Socket socket = new Socket();
        try {
            socket.setSoTimeout(30_000);

            ClientHandler.disableReadTimeout(socket);

            assertEquals(0, socket.getSoTimeout());
        } finally {
            socket.close();
        }
    }
}
