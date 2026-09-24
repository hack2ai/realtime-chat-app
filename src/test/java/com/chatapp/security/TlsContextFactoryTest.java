package com.chatapp.security;

import com.chatapp.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsContextFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void clientContextUsesDefaultJvmTrustStoreWhenNoCustomTrustStoreIsConfigured() {
        String pathProperty = "chatapp.client.tls.trustStorePath";
        String passwordProperty = "chatapp.client.tls.trustStorePassword";
        String previousPath = System.getProperty(pathProperty);
        String previousPassword = System.getProperty(passwordProperty);
        try {
            System.setProperty(pathProperty, "");
            System.clearProperty(passwordProperty);

            SSLContext context = TlsContextFactory.createClientContext();

            assertNotNull(context);
            assertNotNull(context.getSocketFactory());
        } finally {
            restoreProperty(pathProperty, previousPath);
            restoreProperty(passwordProperty, previousPassword);
        }
    }

    @Test
    void clientContextLoadsConfiguredPkcs12TrustStore() throws Exception {
        String pathProperty = "chatapp.client.tls.trustStorePath";
        String passwordProperty = "chatapp.client.tls.trustStorePassword";
        String previousPath = System.getProperty(pathProperty);
        String previousPassword = System.getProperty(passwordProperty);
        String password = "test-password";
        Path trustStorePath = tempDir.resolve("client-truststore.p12");
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, password.toCharArray());
            try (var output = java.nio.file.Files.newOutputStream(trustStorePath)) {
                trustStore.store(output, password.toCharArray());
            }

            System.setProperty(pathProperty, trustStorePath.toString());
            System.setProperty(passwordProperty, password);

            SSLContext context = TlsContextFactory.createClientContext();

            assertNotNull(context);
            assertNotNull(context.getSocketFactory());
        } finally {
            restoreProperty(pathProperty, previousPath);
            restoreProperty(passwordProperty, previousPassword);
        }
    }

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

    @Test
    void certificateExpiryWarningDaysCanBeConfiguredWithinSafeBounds() {
        String property = "chatapp.tls.certificateExpiryWarningDays";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "45");
            assertEquals(45, AppConfig.getTlsCertificateExpiryWarningDays());
        } finally {
            restoreProperty(property, previous);
        }
    }

    @Test
    void certificateExpiryWarningDaysRejectsValuesOutsideSafeBounds() {
        String property = "chatapp.tls.certificateExpiryWarningDays";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "-1");
            assertThrows(IllegalStateException.class, AppConfig::getTlsCertificateExpiryWarningDays);

            System.setProperty(property, "3651");
            assertThrows(IllegalStateException.class, AppConfig::getTlsCertificateExpiryWarningDays);
        } finally {
            restoreProperty(property, previous);
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
