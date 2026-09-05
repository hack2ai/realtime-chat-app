package com.chatapp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {
    private static final String[] DB_KEYS = {
            "host", "port", "name", "user", "password"
    };

    @AfterEach
    void clearDatabaseOverrides() {
        for (String key : DB_KEYS) {
            System.clearProperty("chatapp.db." + key);
            System.clearProperty("chatapp.db." + key + ".file");
        }
        System.clearProperty("chatapp.db.useSsl");
        System.clearProperty("chatapp.db.allowPublicKeyRetrieval");
        System.clearProperty("chatapp.db.connectTimeoutMs");
        System.clearProperty("chatapp.tls.enabled");
        System.clearProperty("chatapp.client.tls.enabled");
    }

    @Test
    void jdbcUrlUsesJavaUtf8EncodingName() {
        System.setProperty("chatapp.db.host", "localhost");
        System.setProperty("chatapp.db.port", "3306");
        System.setProperty("chatapp.db.name", "chatapp_db");
        System.setProperty("chatapp.db.user", "chatapp_user");
        System.setProperty("chatapp.db.password", "test-password");
        System.setProperty("chatapp.db.useSsl", "false");
        System.setProperty("chatapp.db.allowPublicKeyRetrieval", "false");

        String jdbcUrl = AppConfig.getJdbcUrl();

        assertTrue(jdbcUrl.contains("characterEncoding=UTF-8"));
        assertTrue(jdbcUrl.contains("useUnicode=true"));
        assertTrue(jdbcUrl.contains("connectTimeout=10000"));
        assertFalse(jdbcUrl.contains("characterEncoding=utf8mb4"));
    }

    @Test
    void jdbcUrlUsesConfiguredConnectTimeout() {
        System.setProperty("chatapp.db.host", "localhost");
        System.setProperty("chatapp.db.port", "3306");
        System.setProperty("chatapp.db.name", "chatapp_db");
        System.setProperty("chatapp.db.user", "chatapp_user");
        System.setProperty("chatapp.db.password", "test-password");
        System.setProperty("chatapp.db.connectTimeoutMs", "2500");

        assertTrue(AppConfig.getJdbcUrl().contains("connectTimeout=2500"));
    }

    @Test
    void applicationTlsIsDisabledByDefault() {
        System.clearProperty("chatapp.tls.enabled");

        assertFalse(AppConfig.isTlsEnabled());
    }

    @Test
    void applicationTlsCanBeEnabledThroughJvmOverride() {
        System.setProperty("chatapp.tls.enabled", "true");

        assertTrue(AppConfig.isTlsEnabled());
    }

    @Test
    void clientTlsIsDisabledByDefault() {
        System.clearProperty("chatapp.client.tls.enabled");

        assertFalse(AppConfig.isClientTlsEnabled());
    }

    @Test
    void clientTlsCanBeEnabledThroughJvmOverride() {
        System.setProperty("chatapp.client.tls.enabled", "true");

        assertTrue(AppConfig.isClientTlsEnabled());
    }

    @Test
    void databasePasswordCanBeLoadedFromJvmSecretFile(@TempDir Path tempDir) throws Exception {
        Path secretFile = tempDir.resolve("db-password");
        Files.writeString(secretFile, "file-based-secret\n");
        System.setProperty("chatapp.db.password.file", secretFile.toString());

        assertEquals("file-based-secret", AppConfig.getDbPassword());
    }
}