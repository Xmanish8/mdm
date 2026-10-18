#!/bin/sh
(
set -eu
for secret in db.password root.password provision.password bootstrap.sql; do
    if [ ! -s "/etc/secrets/$secret" ] || [ ! -r "/etc/secrets/$secret" ]; then
        echo "Required Render initialization secret is missing or unreadable: $secret" >&2
        exit 1
    fi
done
. /opt/mdm/permissions.sh
export MYSQL_PWD="$(cat /etc/secrets/root.password)"
mysql --protocol=socket -uroot mdm < /etc/secrets/bootstrap.sql
mysql --protocol=socket -uroot mdm <<'SQL'
ALTER USER 'mdm_provisioner'@'%' ACCOUNT LOCK;
SHOW GRANTS FOR 'mdm_app'@'%';
SELECT COUNT(*) AS administrator_accounts FROM users WHERE role='ADMIN';
SQL
unset MYSQL_PWD
)
