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
    private static final String TLS_KEY_STORE_PASSWORD = "chatapp.tls.keyStorePassword";
    private static final String CLIENT_TLS_ENABLED = "chatapp.client.tls.enabled";
    private static final String TRUST_STORE_PATH = "chatapp.client.tls.trustStorePath";
    private static final String TRUST_STORE_PASSWORD = "chatapp.client.tls.trustStorePassword";
    private static final String DB_PASSWORD = "chatapp.db.password";
    private static final String DB_SSL_MODE = "chatapp.db.sslMode";
    private static final String DB_USE_SSL = "chatapp.db.useSsl";
    private static final String DB_POOL_MIN_IDLE = "chatapp.db.pool.minIdle";
    private static final String DB_POOL_MAX_SIZE = "chatapp.db.pool.maxSize";
    private static final String SERVER_PORT = "chatapp.server.port";
    private static final String SERVER_MAX_CLIENTS = "chatapp.server.maxClients";
    private static final String BCRYPT_STRENGTH = "chatapp.auth.bcrypt.strength";
    private static final String SESSION_EXPIRY_HOURS = "chatapp.auth.session.expiryHours";
    private static final String SOCKET_READ_TIMEOUT_MS = "chatapp.server.socketReadTimeoutMs";

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

    @Test
    void invalidClientTlsFlagIsRejected() {
        System.setProperty(CLIENT_TLS_ENABLED, "enabled");

        assertThrows(IllegalStateException.class, AppConfig::isClientTlsEnabled);
    }

    @Test
    void databaseTlsDefaultsToVerifiedIdentity() {
        System.setProperty(DB_USE_SSL, "true");
        assertEquals("VERIFY_IDENTITY", AppConfig.getDbSslMode());
    }

    @Test
    void databaseTlsCanBeExplicitlyDisabledForTrustedLocalDevelopment() {
        System.setProperty(DB_SSL_MODE, "DISABLED");
        assertEquals("DISABLED", AppConfig.getDbSslMode());
    }

    @Test
    void databaseTlsModeAcceptsSupportedValues() {
        for (String mode : new String[]{"DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"}) {
            System.setProperty(DB_SSL_MODE, mode);
            assertEquals(mode, AppConfig.getDbSslMode());
        }
    }

    @Test
    void invalidDatabaseTlsModeIsRejected() {
        System.setProperty(DB_SSL_MODE, "VERIFY_HOSTNAME");

        assertThrows(IllegalStateException.class, AppConfig::getDbSslMode);
    }

    @Test
    void jdbcUrlUsesExplicitDatabaseTlsMode() {
        System.setProperty(DB_SSL_MODE, "VERIFY_IDENTITY");
        String url = AppConfig.getJdbcUrl();

        assertTrue(url.contains("sslMode=VERIFY_IDENTITY"));
        assertTrue(url.contains("allowPublicKeyRetrieval=false"));
        assertTrue(url.contains("connectTimeout=1000"));
        assertTrue(url.contains("socketTimeout=60000"));
    }

    @Test
    void placeholderDatabasePasswordIsRejected() {
        System.setProperty(DB_PASSWORD, "CHANGE_ME");

        assertThrows(IllegalStateException.class, AppConfig::getDbPassword);
    }

    @Test
    void configuredDatabasePasswordIsAccepted() {
        System.setProperty(DB_PASSWORD, "test-secret");

        assertEquals("test-secret", AppConfig.getDbPassword());
    }

    @Test
    void placeholderTlsKeyStorePasswordIsRejected() {
        System.setProperty(TLS_KEY_STORE_PASSWORD, "CHANGE_ME");

        assertThrows(IllegalStateException.class, AppConfig::getTlsKeyStorePassword);
    }

    @Test
    void configuredTlsKeyStorePasswordIsAccepted() {
        System.setProperty(TLS_KEY_STORE_PASSWORD, "test-secret");

        assertEquals("test-secret", AppConfig.getTlsKeyStorePassword());
    }

    @Test
    void bcryptStrengthRejectsValuesOutsideSupportedRange() {
        System.setProperty(BCRYPT_STRENGTH, "9");
        assertThrows(IllegalStateException.class, AppConfig::getBcryptStrength);

        System.setProperty(BCRYPT_STRENGTH, "32");
        assertThrows(IllegalStateException.class, AppConfig::getBcryptStrength);

        System.setProperty(BCRYPT_STRENGTH, "12");
        assertEquals(12, AppConfig.getBcryptStrength());
    }

    @Test
    void sessionExpiryRejectsValuesOutsideSupportedRange() {
        System.setProperty(SESSION_EXPIRY_HOURS, "0");
        assertThrows(IllegalStateException.class, AppConfig::getSessionExpiryHours);

        System.setProperty(SESSION_EXPIRY_HOURS, "8761");
        assertThrows(IllegalStateException.class, AppConfig::getSessionExpiryHours);

        System.setProperty(SESSION_EXPIRY_HOURS, "24");
        assertEquals(24, AppConfig.getSessionExpiryHours());
    }

    @Test
    void socketReadTimeoutRejectsValuesOutsideSupportedRange() {
        System.setProperty(SOCKET_READ_TIMEOUT_MS, "-1");
        assertThrows(IllegalStateException.class, AppConfig::getSocketReadTimeoutMs);

        System.setProperty(SOCKET_READ_TIMEOUT_MS, "300001");
        assertThrows(IllegalStateException.class, AppConfig::getSocketReadTimeoutMs);

        System.setProperty(SOCKET_READ_TIMEOUT_MS, "120000");
        assertEquals(120000, AppConfig.getSocketReadTimeoutMs());
    }

    @Test
    void serverPortRejectsValuesOutsideTcpRange() {
        System.setProperty(SERVER_PORT, "0");
        assertThrows(IllegalStateException.class, AppConfig::getServerPort);

        System.setProperty(SERVER_PORT, "65536");
        assertThrows(IllegalStateException.class, AppConfig::getServerPort);

        System.setProperty(SERVER_PORT, "5050");
        assertEquals(5050, AppConfig.getServerPort());
    }

    @Test
    void serverMaxClientsRejectsValuesOutsideSupportedRange() {
        System.setProperty(SERVER_MAX_CLIENTS, "0");
        assertThrows(IllegalStateException.class, AppConfig::getServerMaxClients);

        System.setProperty(SERVER_MAX_CLIENTS, "10001");
        assertThrows(IllegalStateException.class, AppConfig::getServerMaxClients);

        System.setProperty(SERVER_MAX_CLIENTS, "200");
        assertEquals(200, AppConfig.getServerMaxClients());
    }

    @Test
    void databasePoolRejectsValuesOutsideSupportedBounds() {
        System.setProperty(DB_POOL_MIN_IDLE, "0");
        assertThrows(IllegalStateException.class, AppConfig::getDbPoolMinIdle);

        System.setProperty(DB_POOL_MIN_IDLE, "51");
        assertThrows(IllegalStateException.class, AppConfig::getDbPoolMinIdle);

        System.setProperty(DB_POOL_MIN_IDLE, "5");
        assertEquals(5, AppConfig.getDbPoolMinIdle());

        System.setProperty(DB_POOL_MAX_SIZE, "0");
        assertThrows(IllegalStateException.class, AppConfig::getDbPoolMaxSize);

        System.setProperty(DB_POOL_MAX_SIZE, "101");
        assertThrows(IllegalStateException.class, AppConfig::getDbPoolMaxSize);

        System.setProperty(DB_POOL_MAX_SIZE, "100");
        assertEquals(100, AppConfig.getDbPoolMaxSize());
    }

    @Test
    void databasePoolRejectsMaxSizeBelowMinIdle() {
        System.setProperty(DB_POOL_MIN_IDLE, "5");
        System.setProperty(DB_POOL_MAX_SIZE, "4");
        assertThrows(IllegalStateException.class, AppConfig::getDbPoolMaxSize);

        System.setProperty(DB_POOL_MAX_SIZE, "5");
        assertEquals(5, AppConfig.getDbPoolMaxSize());
    }

    private static void clearProperties() {
        System.clearProperty(TLS_ENABLED);
        System.clearProperty(TLS_KEY_STORE_PASSWORD);
        System.clearProperty(CLIENT_TLS_ENABLED);
        System.clearProperty(TRUST_STORE_PATH);
        System.clearProperty(TRUST_STORE_PASSWORD);
        System.clearProperty(DB_PASSWORD);
        System.clearProperty(DB_SSL_MODE);
        System.clearProperty(DB_USE_SSL);
        System.clearProperty(DB_POOL_MIN_IDLE);
        System.clearProperty(DB_POOL_MAX_SIZE);
        System.clearProperty(SERVER_PORT);
        System.clearProperty(SERVER_MAX_CLIENTS);
        System.clearProperty(BCRYPT_STRENGTH);
        System.clearProperty(SESSION_EXPIRY_HOURS);
        System.clearProperty(SOCKET_READ_TIMEOUT_MS);
    }
}
