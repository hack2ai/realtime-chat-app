package com.chatapp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
        }
        System.clearProperty("chatapp.db.useSsl");
        System.clearProperty("chatapp.db.allowPublicKeyRetrieval");
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
        assertFalse(jdbcUrl.contains("characterEncoding=utf8mb4"));
    }
}
