-- Run deliberately as a database administrator against the production mdm database.
-- First apply database/migrations/20261018-password-reset.sql to that database.
GRANT UPDATE (password_hash) ON mdm.users TO 'mdm_app'@'%';
GRANT SELECT,INSERT,DELETE ON mdm.password_reset_tokens TO 'mdm_app'@'%';
