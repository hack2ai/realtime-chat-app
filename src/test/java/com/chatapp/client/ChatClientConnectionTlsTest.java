package com.chatapp.client;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatClientConnectionTlsTest {

    @Test
    void configureTlsSocketEnablesHostnameVerificationAndModernProtocols() throws Exception {
        SSLSocket socket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket();

        ChatClientConnection.configureTlsSocket(socket);

        assertEquals("HTTPS", socket.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertEquals(2, socket.getEnabledProtocols().length);
        assertEquals("TLSv1.3", socket.getEnabledProtocols()[0]);
        assertEquals("TLSv1.2", socket.getEnabledProtocols()[1]);

        socket.close();
    }

    @Test
    void configureTlsSocketRejectsNullSocket() {
        assertThrows(IllegalArgumentException.class, () -> ChatClientConnection.configureTlsSocket(null));
    }
}
