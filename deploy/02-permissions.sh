#!/bin/sh
(
set -eu
provision_password="$(cat /run/secrets/provision.password)"
case "$provision_password" in ''|*[!0-9a-f]*) echo 'Provisioning password must be generated hex' >&2; exit 1;; esac
export MYSQL_PWD="$(cat /run/secrets/root.password)"
mysql --protocol=socket -uroot mdm <<SQL
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'mdm_app'@'%';
ALTER USER 'mdm_app'@'%' REQUIRE SSL;
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
CREATE USER IF NOT EXISTS 'mdm_provisioner'@'%' IDENTIFIED BY '${provision_password}' REQUIRE SSL;
GRANT SELECT,INSERT ON mdm.users TO 'mdm_provisioner'@'%';
SQL
unset MYSQL_PWD provision_password
)
