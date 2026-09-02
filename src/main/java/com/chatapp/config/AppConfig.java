package com.chatapp.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/** Centralized runtime configuration with file, environment, and JVM overrides. */
public final class AppConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static final String ENV_PREFIX = "CHATAPP_";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) PROPERTIES.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + CONFIG_FILE, e);
        }
    }

    private AppConfig() {}

    private static String require(String key) {
        String value = System.getProperty("chatapp." + key);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENV_PREFIX + key.replace('.', '_').toUpperCase(Locale.ROOT));
        }
        if (value == null || value.isBlank()) value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required config key: " + key);
        return value.trim();
    }

    private static int requireInt(String key) {
        try { return Integer.parseInt(require(key)); }
        catch (NumberFormatException e) { throw new IllegalStateException("Config key '" + key + "' must be an integer.", e); }
    }

    public static String getDbHost() { return require("db.host"); }
    public static int getDbPort() { return requireInt("db.port"); }
    public static String getDbName() { return require("db.name"); }
    public static String getDbUser() { return require("db.user"); }
    public static String getDbPassword() { return require("db.password"); }
    public static int getDbPoolMinIdle() { return requireInt("db.pool.minIdle"); }
    public static int getDbPoolMaxSize() { return requireInt("db.pool.maxSize"); }
    public static int getDbConnectionTimeoutMs() { return requireInt("db.pool.connectionTimeoutMs"); }
    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true";
    }
    public static int getServerPort() { return requireInt("server.port"); }
    public static int getServerMaxClients() { return requireInt("server.maxClients"); }
    public static int getSocketReadTimeoutMs() { return requireInt("server.socketReadTimeoutMs"); }
    public static int getBcryptStrength() { return requireInt("auth.bcrypt.strength"); }
    public static int getSessionExpiryHours() { return requireInt("auth.session.expiryHours"); }
    public static String getAttachmentStoragePath() { return require("attachments.storagePath"); }
}
