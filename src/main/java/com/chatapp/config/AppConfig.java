package com.chatapp.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads and exposes application configuration from
 * {@code config.properties} on the classpath.
 *
 * <p>This is a small eager-loaded singleton: configuration is read once
 * at class-load time and cached, since config values do not change at
 * runtime and every part of the app (server, client, DAOs) needs them.
 *
 * <p>Fails fast (throws {@link ExceptionInInitializerError} via a runtime
 * exception) if the properties file is missing or a required key is
 * absent, rather than letting a {@code null} silently propagate into a
 * JDBC URL or socket bind call where the resulting error would be far
 * more confusing to debug.
 */
public final class AppConfig {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Configuration file '" + CONFIG_FILE + "' not found on classpath. " +
                        "Copy 'config.properties.example' to 'config.properties' " +
                        "in src/main/resources and fill in your local settings."
                );
            }
            PROPERTIES.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + CONFIG_FILE, e);
        }
    }

    private AppConfig() {
        // Static utility class; no instances.
    }

    // ---------------------------------------------------------------
    // Generic accessors
    // ---------------------------------------------------------------

    private static String require(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return value.trim();
    }

    private static int requireInt(String key) {
        String value = require(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Config key '" + key + "' must be an integer, got: " + value);
        }
    }

    // ---------------------------------------------------------------
    // Database
    // ---------------------------------------------------------------

    public static String getDbHost()              { return require("db.host"); }
    public static int    getDbPort()               { return requireInt("db.port"); }
    public static String getDbName()               { return require("db.name"); }
    public static String getDbUser()                { return require("db.user"); }
    public static String getDbPassword()            { return require("db.password"); }
    public static int    getDbPoolMinIdle()          { return requireInt("db.pool.minIdle"); }
    public static int    getDbPoolMaxSize()          { return requireInt("db.pool.maxSize"); }
    public static int    getDbConnectionTimeoutMs()  { return requireInt("db.pool.connectionTimeoutMs"); }

    /**
     * Builds the full JDBC URL from the individual host/port/name
     * properties, with sensible flags set for a MySQL 8 server
     * (UTC time handling, UTF-8, no legacy SSL-required nagging).
     */
    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName()
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&characterEncoding=utf8"
                + "&useUnicode=true";
    }

    // ---------------------------------------------------------------
    // Server
    // ---------------------------------------------------------------

    public static int getServerPort()                    { return requireInt("server.port"); }
    public static int getServerMaxClients()               { return requireInt("server.maxClients"); }
    public static int getHeartbeatIntervalSeconds()       { return requireInt("server.heartbeatIntervalSeconds"); }
    public static int getHeartbeatTimeoutSeconds()        { return requireInt("server.heartbeatTimeoutSeconds"); }

    // ---------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------

    public static int  getBcryptStrength()         { return requireInt("auth.bcrypt.strength"); }
    public static int  getSessionExpiryHours()      { return requireInt("auth.session.expiryHours"); }
}
