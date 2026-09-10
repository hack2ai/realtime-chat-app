package com.chatapp.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ChatServerTransportSecurityTest {
    @Test
    void loopbackPlaintextBindingIsAllowedByDefault() throws UnknownHostException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        assertDoesNotThrow(() -> ChatServer.validateTransportSecurity(loopback, false, false));
    }

    @Test
    void anyLocalPlaintextBindingIsRejectedByDefault() throws UnknownHostException {
        InetAddress anyLocal = InetAddress.getByName("0.0.0.0");

        assertThrows(IOException.class,
                () -> ChatServer.validateTransportSecurity(anyLocal, false, false));
    }

    @Test
    void remotePlaintextBindingCanBeExplicitlyEnabled() throws UnknownHostException {
        InetAddress remote = InetAddress.getByName("192.0.2.10");

        assertDoesNotThrow(() -> ChatServer.validateTransportSecurity(remote, false, true));
    }

    @Test
    void remoteBindingIsAllowedWhenTlsIsEnabled() throws UnknownHostException {
        InetAddress remote = InetAddress.getByName("192.0.2.10");

        assertDoesNotThrow(() -> ChatServer.validateTransportSecurity(remote, true, false));
    }
}
