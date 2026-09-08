package com.chatapp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AppConfigTest {
    private static final String[] DB_KEYS = {
            "host", "port", "name", "user", "password"
    };

    @AfterEach
    void clearConfigurationOverrides() {
        for (String key : DB_KEYS) {
            System.clearProperty("chatapp.db." + key);
            System.clearProperty("chatapp.db." + key + ".file");
        }
        System.clearProperty("chatapp.db.useSsl");
        System.clearProperty("chatapp.db.allowPublicKeyRetrieval");
        System.clearProperty("chatapp.db.connectTimeoutMs");
        System.clearProperty("chatapp.db.socketTimeoutMs");
        System.clearProperty("chatapp.tls.enabled");
        System.clearProperty("chatapp.client.tls.enabled");
    }

    @Test
    void jdbcUrlUsesJavaUtf8EncodingName() {
        setRequiredDatabaseProperties();
        System.setProperty("chatapp.db.useSsl", "false");
        System.setProperty("chatapp.db.allowPublicKeyRetrieval", "false");
        System.setProperty("chatapp.db.connectTimeoutMs", "10000");
        System.setProperty("chatapp.db.socketTimeoutMs", "120000");

        String jdbcUrl = AppConfig.getJdbcUrl();

        assertTrue(jdbcUrl.contains("characterEncoding=UTF-8"));
        assertTrue(jdbcUrl.contains("useUnicode=true"));
        assertTrue(jdbcUrl.contains("connectTimeout=10000"));
        assertTrue(jdbcUrl.contains("socketTimeout=120000"));
        assertFalse(jdbcUrl.contains("characterEncoding=utf8mb4"));
    }

    @Test
    void jdbcUrlUsesConfiguredConnectTimeout() {
        setRequiredDatabaseProperties();
        System.setProperty("chatapp.db.connectTimeoutMs", "2500");

        assertTrue(AppConfig.getJdbcUrl().contains("connectTimeout=2500"));
    }

    @Test
    void jdbcUrlUsesConfiguredSocketTimeout() {
        setRequiredDatabaseProperties();
        System.setProperty("chatapp.db.socketTimeoutMs", "15000");

        assertTrue(AppConfig.getJdbcUrl().contains("socketTimeout=15000"));
    }

    @Test
    void databaseNameAcceptsSafeIdentifier() {
        System.setProperty("chatapp.db.name", "chatapp_db_2026");

        assertEquals("chatapp_db_2026", AppConfig.getDbName());
    }

    @Test
    void databaseNameRejectsJdbcQuerySuffix() {
        System.setProperty("chatapp.db.name", "chatapp_db?useSSL=false");

        assertThrows(IllegalStateException.class, AppConfig::getDbName);
    }

    @Test
    void databaseNameRejectsPathDelimiters() {
        System.setProperty("chatapp.db.name", "chatapp_db/other");

        assertThrows(IllegalStateException.class, AppConfig::getDbName);
    }

    @Test
    void databaseTlsIsEnabledByDefault() {
        System.clearProperty("chatapp.db.useSsl");

        assertTrue(AppConfig.isDbUseSsl());
    }

    @Test
    void publicKeyRetrievalIsDisabledByDefault() {
        System.clearProperty("chatapp.db.allowPublicKeyRetrieval");

        assertFalse(AppConfig.isDbAllowPublicKeyRetrieval());
    }

    @Test
    void invalidDatabaseTlsSettingIsRejected() {
        System.setProperty("chatapp.db.useSsl", "maybe");

        assertThrows(IllegalStateException.class, AppConfig::isDbUseSsl);
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

    @Test
    void databasePasswordRejectsSymlinkedSecretFile(@TempDir Path tempDir) throws Exception {
        Path secretFile = tempDir.resolve("real-secret");
        Path symlink = tempDir.resolve("db-password");
        Files.writeString(secretFile, "file-based-secret\n");
        try {
            Files.createSymbolicLink(symlink, secretFile);
        } catch (UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "Symbolic links are unavailable in this test environment");
        } catch (IOException e) {
            assumeTrue(false, "Symbolic links are unavailable in this test environment: " + e.getMessage());
        }

        System.setProperty("chatapp.db.password.file", symlink.toString());

        assertThrows(IllegalStateException.class, AppConfig::getDbPassword);
    }

    private static void setRequiredDatabaseProperties() {
        System.setProperty("chatapp.db.host", "localhost");
        System.setProperty("chatapp.db.port", "3306");
        System.setProperty("chatapp.db.name", "chatapp_db");
        System.setProperty("chatapp.db.user", "chatapp_user");
        System.setProperty("chatapp.db.password", "test-password");
    }
}
