-- Fresh Aiven mdm database only, after schema.sql has been imported.
-- Run as avnadmin over verified TLS in MySQL Workbench.
-- Replace the password placeholder in a PRIVATE editor tab, never in Git.
-- Use the generated 64-character hexadecimal .secrets/db.password value.
-- If mdm_app already exists, STOP and inspect its grants before continuing.
CREATE USER 'mdm_app'@'%' IDENTIFIED BY 'REPLACE_WITH_PRIVATE_GENERATED_DB_PASSWORD' REQUIRE SSL;
GRANT SELECT ON mdm.* TO 'mdm_app'@'%';
GRANT INSERT (name,email,password_hash) ON mdm.users TO 'mdm_app'@'%';
GRANT UPDATE (password_hash) ON mdm.users TO 'mdm_app'@'%';
GRANT INSERT ON mdm.teams TO 'mdm_app'@'%';
GRANT INSERT ON mdm.team_memberships TO 'mdm_app'@'%';
GRANT INSERT,UPDATE ON mdm.work_items TO 'mdm_app'@'%';
GRANT INSERT ON mdm.activity_logs TO 'mdm_app'@'%';
GRANT INSERT,UPDATE ON mdm.operation_requests TO 'mdm_app'@'%';
GRANT INSERT ON mdm.admin_activity TO 'mdm_app'@'%';
GRANT INSERT,UPDATE,DELETE ON mdm.auth_rate_limits TO 'mdm_app'@'%';
GRANT INSERT,UPDATE,DELETE ON mdm.analytics_daily TO 'mdm_app'@'%';
GRANT SELECT,INSERT,DELETE ON mdm.password_reset_tokens TO 'mdm_app'@'%';
SHOW GRANTS FOR 'mdm_app'@'%';
