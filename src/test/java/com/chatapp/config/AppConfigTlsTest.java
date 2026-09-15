package com.chatapp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTlsTest {
    private static final String TLS_ENABLED = "chatapp.tls.enabled";
    private static final String CLIENT_TLS_ENABLED = "chatapp.client.tls.enabled";
    private static final String TRUST_STORE_PATH = "chatapp.client.tls.trustStorePath";
    private static final String TRUST_STORE_PASSWORD = "chatapp.client.tls.trustStorePassword";

    @BeforeEach
    void clearOverrides() {
        clearProperties();
    }

    @AfterEach
    void restoreOverrides() {
        clearProperties();
    }

    @Test
    void serverTlsIsDisabledByDefault() {
        assertFalse(AppConfig.isTlsEnabled());
    }

    @Test
    void serverTlsFlagAcceptsBooleanOverrides() {
        System.setProperty(TLS_ENABLED, "true");
        assertTrue(AppConfig.isTlsEnabled());

        System.setProperty(TLS_ENABLED, "false");
        assertFalse(AppConfig.isTlsEnabled());
    }

    @Test
    void invalidServerTlsFlagIsRejected() {
        System.setProperty(TLS_ENABLED, "yes");

        assertThrows(IllegalStateException.class, AppConfig::isTlsEnabled);
    }

    @Test
    void clientTrustStoreOverridesAreResolved() {
        System.setProperty(CLIENT_TLS_ENABLED, "true");
        System.setProperty(TRUST_STORE_PATH, "config/client-truststore.p12");
        System.setProperty(TRUST_STORE_PASSWORD, "test-secret");

        assertTrue(AppConfig.isClientTlsEnabled());
        assertEquals("config/client-truststore.p12", AppConfig.getTlsTrustStorePath());
        assertEquals("test-secret", AppConfig.getTlsTrustStorePassword());
    }

    private static void clearProperties() {
        System.clearProperty(TLS_ENABLED);
        System.clearProperty(CLIENT_TLS_ENABLED);
        System.clearProperty(TRUST_STORE_PATH);
        System.clearProperty(TRUST_STORE_PASSWORD);
    }
}
