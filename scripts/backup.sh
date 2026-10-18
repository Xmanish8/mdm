#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${AGE_RECIPIENT:?Set your age public recipient; never put the private key on this server}"
command -v age >/dev/null
mkdir -p backups
file="backups/mdm-$(date -u +%Y%m%dT%H%M%SZ).sql.age"
docker compose exec -T db sh -c 'MYSQL_PWD="$(cat /run/secrets/root.password)" exec mysqldump -uroot --single-transaction --routines --triggers mdm' | age -r "$AGE_RECIPIENT" -o "$file"
printf 'Encrypted backup written: %s\n' "$file"
