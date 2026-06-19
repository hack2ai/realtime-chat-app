-- ════════════════════════════════════════════════════════════════════
--  Real-Time Chat Application — Database Schema
--  MySQL 8.0+
--
--  Usage:
--      mysql -u root -p < schema.sql
--
--  This script is idempotent (safe to re-run): it creates the database,
--  application user, and tables only if they don't already exist.
-- ════════════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS chatapp_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chatapp_db;

-- ── Application database user ──────────────────────────────────────
-- Matches the credentials in config.properties.example. Change the
-- password here AND in config.properties for anything beyond local dev.
CREATE USER IF NOT EXISTS 'chatapp_user'@'localhost' IDENTIFIED BY 'chatapp_pass';
GRANT ALL PRIVILEGES ON chatapp_db.* TO 'chatapp_user'@'localhost';
FLUSH PRIVILEGES;


-- ════════════════════════════════════════════════════════════════════
-- Users
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS users (
    id             INT             PRIMARY KEY AUTO_INCREMENT,
    username       VARCHAR(30)     NOT NULL UNIQUE,
    email          VARCHAR(120)    NOT NULL UNIQUE,
    password_hash  VARCHAR(255)    NOT NULL,              -- bcrypt hash, never plaintext
    profile_image  VARCHAR(500)    DEFAULT NULL,
    role           ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    status         ENUM('ONLINE','OFFLINE','AWAY') NOT NULL DEFAULT 'OFFLINE',
    last_seen      DATETIME        DEFAULT NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_users_email  (email),
    INDEX idx_users_status (status)
) ENGINE=InnoDB;


-- ════════════════════════════════════════════════════════════════════
-- Private Messages (1:1 chat)
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS private_messages (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    sender_id    INT          NOT NULL,
    receiver_id  INT          NOT NULL,
    message      TEXT         NOT NULL,
    msg_status   ENUM('SENT','DELIVERED','READ') NOT NULL DEFAULT 'SENT',
    sent_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (sender_id)   REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,

    INDEX idx_pm_sender    (sender_id),
    INDEX idx_pm_receiver  (receiver_id),
    -- Composite index supports "conversation between A and B, ordered by time"
    -- which is the dominant query pattern for chat history retrieval.
    INDEX idx_pm_conversation (sender_id, receiver_id, sent_at)
) ENGINE=InnoDB;


-- ════════════════════════════════════════════════════════════════════
-- Groups
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS chat_groups (
    id           INT          PRIMARY KEY AUTO_INCREMENT,
    group_name   VARCHAR(50)  NOT NULL,
    description  VARCHAR(255) DEFAULT NULL,
    created_by   INT          NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,

    INDEX idx_groups_created_by (created_by)
) ENGINE=InnoDB;


-- ════════════════════════════════════════════════════════════════════
-- Group Members
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS group_members (
    id         INT      PRIMARY KEY AUTO_INCREMENT,
    group_id   INT      NOT NULL,
    user_id    INT      NOT NULL,
    role       ENUM('MEMBER','ADMIN') NOT NULL DEFAULT 'MEMBER',
    joined_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_group_member (group_id, user_id),  -- prevents duplicate membership
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)  REFERENCES users(id)       ON DELETE CASCADE,

    INDEX idx_gm_group (group_id),
    INDEX idx_gm_user  (user_id)
) ENGINE=InnoDB;


-- ════════════════════════════════════════════════════════════════════
-- Group Messages
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS group_messages (
    id         BIGINT   PRIMARY KEY AUTO_INCREMENT,
    group_id   INT      NOT NULL,
    sender_id  INT      NOT NULL,
    message    TEXT     NOT NULL,
    sent_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (group_id)  REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id)       ON DELETE CASCADE,

    INDEX idx_gmsg_group_time (group_id, sent_at)
) ENGINE=InnoDB;


-- ════════════════════════════════════════════════════════════════════
-- Seed data: default admin account
-- Username: admin   |   Password: Admin@123
--
-- The hash below was generated and verified with bcrypt (cost factor 12)
-- against the literal string "Admin@123" — it is not a placeholder.
-- CHANGE THIS PASSWORD immediately in any shared or non-local environment;
-- it is published in this file and therefore not a secret.
-- ════════════════════════════════════════════════════════════════════
INSERT IGNORE INTO users (username, email, password_hash, role, status)
VALUES (
    'admin',
    'admin@chatapp.local',
    '$2b$12$DZ6D.G/D/VQG3SKNjuFZPeKNHCMKre5204h62M30unldZi/Zk/nKm',
    'ADMIN',
    'OFFLINE'
);
