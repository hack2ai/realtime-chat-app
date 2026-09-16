-- The Compose-created runtime account only needs CRUD access to application data.
-- Schema changes and privilege management remain the responsibility of the
-- administrative/root account used to initialize or migrate the database.
REVOKE ALL PRIVILEGES, GRANT OPTION ON `chatapp_db`.* FROM 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.`users` TO 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.`private_messages` TO 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.`private_attachments` TO 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.`chat_groups` TO 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.`group_members` TO 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.`group_messages` TO 'chatapp_user'@'%';
