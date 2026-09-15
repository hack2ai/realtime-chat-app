-- The Compose-created runtime account only needs CRUD access to application data.
-- Schema changes and privilege management remain the responsibility of the
-- administrative/root account used to initialize or migrate the database.
REVOKE ALL PRIVILEGES, GRANT OPTION ON `chatapp_db`.* FROM 'chatapp_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `chatapp_db`.* TO 'chatapp_user'@'%';
