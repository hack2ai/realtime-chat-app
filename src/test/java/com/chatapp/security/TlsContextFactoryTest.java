package com.chatapp.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsContextFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void serverContextFailsClosedWhenKeyStoreIsMissing() {
        String pathProperty = "chatapp.tls.keyStorePath";
        String passwordProperty = "chatapp.tls.keyStorePassword";
        String previousPath = System.getProperty(pathProperty);
        String previousPassword = System.getProperty(passwordProperty);
        try {
            System.setProperty(pathProperty, tempDir.resolve("missing-server.p12").toString());
            System.setProperty(passwordProperty, "test-password");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    TlsContextFactory::createServerContext);

            assertTrue(exception.getMessage().contains("Unable to initialize TLS server context."));
        } finally {
            restoreProperty(pathProperty, previousPath);
            restoreProperty(passwordProperty, previousPassword);
        }
    }

    @Test
    void clientContextFailsClosedWhenConfiguredTrustStoreIsMissing() {
        String pathProperty = "chatapp.client.tls.trustStorePath";
        String passwordProperty = "chatapp.client.tls.trustStorePassword";
        String previousPath = System.getProperty(pathProperty);
        String previousPassword = System.getProperty(passwordProperty);
        try {
            System.setProperty(pathProperty, tempDir.resolve("missing-client.p12").toString());
            System.setProperty(passwordProperty, "test-password");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    TlsContextFactory::createClientContext);

            assertTrue(exception.getMessage().contains("Unable to initialize TLS client context."));
        } finally {
            restoreProperty(pathProperty, previousPath);
            restoreProperty(passwordProperty, previousPassword);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
