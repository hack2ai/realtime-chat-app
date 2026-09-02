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
        if (value == null || value.isBlank()) value = System.getenv(ENV_PREFIX + key.replace('.', '_').toUpperCase(Locale.ROOT));
        if (value == null || value.isBlank()) value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required config key: " + key);
        return value.trim();
    }

    private static String optional(String key, String defaultValue) {
        String value = System.getProperty("chatapp." + key);
        if (value == null || value.isBlank()) value = System.getenv(ENV_PREFIX + key.replace('.', '_').toUpperCase(Locale.ROOT));
        if (value == null || value.isBlank()) value = PROPERTIES.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int requireInt(String key) {
        try { return Integer.parseInt(require(key)); }
        catch (NumberFormatException e) { throw new IllegalStateException("Config key '" + key + "' must be an integer.", e); }
    }

    private static int requirePositiveInt(String key) {
        int value = requireInt(key);
        if (value <= 0) throw new IllegalStateException("Config key '" + key + "' must be greater than zero.");
        return value;
    }

    private static int requirePort(String key) {
        int value = requireInt(key);
        if (value < 1 || value > 65535) throw new IllegalStateException("Config key '" + key + "' must be between 1 and 65535.");
        return value;
    }

    private static int requireRange(String key, int min, int max) {
        int value = requireInt(key);
        if (value < min || value > max) throw new IllegalStateException("Config key '" + key + "' must be between " + min + " and " + max + ".");
        return value;
    }

    private static boolean optionalBoolean(String key, boolean defaultValue) {
        String value = optional(key, Boolean.toString(defaultValue));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalStateException("Config key '" + key + "' must be true or false.");
        }
        return Boolean.parseBoolean(value);
    }

    public static String getDbHost() { return require("db.host"); }
    public static int getDbPort() { return requirePort("db.port"); }
    public static String getDbName() { return require("db.name"); }
    public static String getDbUser() { return require("db.user"); }
    public static String getDbPassword() { return require("db.password"); }
    public static int getDbPoolMinIdle() { return requirePositiveInt("db.pool.minIdle"); }
    public static int getDbPoolMaxSize() {
        int value = requirePositiveInt("db.pool.maxSize");
        if (value < getDbPoolMinIdle()) throw new IllegalStateException("Config key 'db.pool.maxSize' must be at least db.pool.minIdle.");
        return value;
    }
    public static int getDbConnectionTimeoutMs() { return requireRange("db.pool.connectionTimeoutMs", 1000, 120000); }
    public static boolean isDbUseSsl() { return optionalBoolean("db.useSsl", true); }
    public static boolean isDbAllowPublicKeyRetrieval() { return optionalBoolean("db.allowPublicKeyRetrieval", false); }
    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName()
                + "?useSSL=" + isDbUseSsl()
                + "&allowPublicKeyRetrieval=" + isDbAllowPublicKeyRetrieval()
                + "&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true";
    }
    public static int getServerPort() { return requirePort("server.port"); }
    public static String getServerBindAddress() { return require("server.bindAddress"); }
    public static int getServerMaxClients() { return requireRange("server.maxClients", 1, 10000); }
    public static int getSocketReadTimeoutMs() { return requireRange("server.socketReadTimeoutMs", 0, 300000); }
    public static int getBcryptStrength() { return requireRange("auth.bcrypt.strength", 10, 31); }
    public static int getSessionExpiryHours() { return requireRange("auth.session.expiryHours", 1, 8760); }
    public static String getAttachmentStoragePath() { return require("attachments.storagePath"); }
}
