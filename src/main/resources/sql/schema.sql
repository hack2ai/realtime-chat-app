-- Real-Time Chat Application — MySQL 8.0+
-- No application credentials or default passwords belong in this schema.
-- Create the database user separately with least privilege.

CREATE DATABASE IF NOT EXISTS chatapp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chatapp_db;

CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(30) NOT NULL UNIQUE,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    profile_image VARCHAR(500) DEFAULT NULL,
    role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    status ENUM('ONLINE','OFFLINE','AWAY') NOT NULL DEFAULT 'OFFLINE',
    last_seen DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email), INDEX idx_users_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS private_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id INT NOT NULL, receiver_id INT NOT NULL, message TEXT NOT NULL,
    msg_status ENUM('SENT','DELIVERED','READ') NOT NULL DEFAULT 'SENT',
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_pm_sender (sender_id), INDEX idx_pm_receiver (receiver_id),
    INDEX idx_pm_conversation (sender_id, receiver_id, sent_at),
    INDEX idx_pm_reverse_conversation (receiver_id, sender_id, sent_at)
) ENGINE=InnoDB;

-- Attachment metadata is kept in MySQL; file bytes live outside the database.
-- Access is authorized by sender/receiver checks in the service layer.
CREATE TABLE IF NOT EXISTS private_attachments (
    id CHAR(36) PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    file_name VARCHAR(180) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_attachment_sender (sender_id, created_at),
    INDEX idx_attachment_receiver (receiver_id, created_at),
    INDEX idx_attachment_pair (sender_id, receiver_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS chat_groups (
    id INT PRIMARY KEY AUTO_INCREMENT,
    group_name VARCHAR(50) NOT NULL, description VARCHAR(255) DEFAULT NULL,
    created_by INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_groups_created_by (created_by)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS group_members (
    id INT PRIMARY KEY AUTO_INCREMENT, group_id INT NOT NULL, user_id INT NOT NULL,
    role ENUM('MEMBER','ADMIN') NOT NULL DEFAULT 'MEMBER', joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_group_member (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_gm_group (group_id), INDEX idx_gm_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS group_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, group_id INT NOT NULL, sender_id INT NOT NULL,
    message TEXT NOT NULL, sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_gmsg_group_time (group_id, sent_at)
) ENGINE=InnoDB;
