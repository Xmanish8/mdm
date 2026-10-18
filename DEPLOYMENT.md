# Deployment runbook

## 1. Prepare the host and domain

Use a Linux server with Docker Engine/Compose. This environment did not have Docker, so container execution remains a release verification step. Point the selected domain's A/AAAA records at the server. Permit inbound TCP 80/443 and UDP 443 if using HTTP/3; restrict SSH to your administrative network. Do not expose 3306 or 8080. Verify IPv6 routing if publishing an AAAA record.

Extract this project into its own directory. Do not replace your existing local database. Compose creates a separate named volume and mdm database; your local opsflow data is not automatically migrated. If moving existing data, take a verified backup and perform an explicit staged import before switching traffic.

## 2. Configuration and secret creation

The delivered .env is intentionally blank for domain/operator values and contains no passwords. In PowerShell run:

    powershell -ExecutionPolicy Bypass -File .\scripts\setup-production.ps1

Enter the domain, legal operator/contact, initial admin email and a strong initial admin password. The script creates cryptographically random 32-byte database/provisioning/rate-limit secrets without printing them. It will not overwrite existing secret files or an already configured .env. On Windows the secret directory ACL is restricted to your account and SYSTEM.

Copy the project and .secrets to the server over a trusted encrypted channel. On Linux, from the project folder:

    chmod 700 .secrets
    sudo chown 10001:10001 .secrets/*
    sudo chmod 400 .secrets/*
    chmod 600 .env

The application/provisioning containers run as UID 10001. Compose file-backed secrets retain host permissions, so this ownership step matters. Keep the parent directory owner-restricted. Never commit or publicly upload .env, .secrets or backups. They are excluded from Git and Docker build contexts. File-backed Compose secrets are not an encrypted vault: enable encrypted host disks and protect backup access on your infrastructure.

No credential is supplied in the archive. Keep the bootstrap password secret only for provisioning. The normal application never receives the root, provisioning or initial-admin password secrets.

## 3. Build and initialize

Review compose.yaml, deploy/Caddyfile and deploy/02-permissions.sh, then run:

    docker compose config --quiet
    docker compose build app
    docker compose up -d db
    docker compose --profile tools run --rm bootstrap
    docker compose up -d app caddy

Use the admin email and chosen initial password at https://YOUR-DOMAIN/login. Initial bootstrap refuses to overwrite an existing account. It uses a separate provisioning database identity; the runtime identity cannot insert or update the users.role column. Admins can then create member accounts and team memberships through /admin.

Caddy obtains/renews certificates and redirects HTTP to HTTPS once DNS and public ports are correct. The app also enforces HTTPS through the production profile. Proxy and database networks are internal; only Caddy publishes ports. The proxy overwrites forwarded headers; do not publish the app port or place an untrusted proxy in front without adapting that trust boundary.

MySQL initialization scripts run only when the named data volume is empty. For an existing volume, schedule a backup and explicit schema/grant migration; restarting does not rerun those scripts. Never use `docker compose down -v` on a database you need to keep.

MySQL transport requires TLS. The bundled single-host network uses sslMode=REQUIRED with MySQL-generated certificates; this encrypts traffic but does not validate server identity. For a managed or multi-host database, install its CA/trust configuration and use VERIFY_IDENTITY. Do not expose the DB publicly. Verify actual grants with SHOW GRANTS before release; no live grant execution was possible here.

Once initial provisioning is complete, a database administrator should disable the mdm_provisioner account until it is explicitly needed again, and securely archive/remove the initial-admin password file. The profile is not a password reset endpoint.

## 4. Production verification

- Confirm HTTP redirects to the correct HTTPS domain and the certificate chain is valid.
- Confirm session Set-Cookie includes Secure, HttpOnly and SameSite=Lax; verify sign-out and session expiry.
- Confirm anonymous work access requires login, members cannot access admin URLs or other teams, and conflicting edits/claims fail safely.
- Confirm login throttling returns 429 and database failure fails closed. Test from a separate controlled client so you do not lock out your own current session.
- Confirm CSP/browser console has no unexpected errors, legal links and the custom 404 work, and analytics sends nothing before consent.
- Confirm the app/DB have no externally published ports and the application DB user has no DDL, role update or audit overwrite/delete privileges.
- Verify disk encryption, firewall rules, encrypted backups and a restore exercise on an isolated database.
- Scan dependencies and final container images; the supplied image tags are versioned but not digest-pinned. Pin tested image digests for an immutable release.
- Run Lighthouse/mobile checks against the real HTTPS host; local loopback timing is not a hosted performance score. Exercise authenticated database pages under representative load.

## 5. Analytics, legal pages and retention

ANALYTICS_ENABLED=false is the default. To opt in, set it true in .env and recreate the app service. Browser consent is still required. Only three public page names and daily counts are stored; no external analytics scripts or API keys are used. Administrators can see /admin/analytics. A scheduled task removes aggregate rows older than 30 days and expired rate buckets older than one day, in bounded batches. Removal timing depends on the app staying running.

The legal pages are organization templates. Configure LEGAL_OPERATOR and LEGAL_CONTACT and review hosting-region, organizational work/audit retention, support arrangements and applicable requirements before release. The application does not automatically delete work or audit records. No jurisdiction-specific compliance certification is claimed.

## 6. Backups and operation

Install age from its official distribution. Keep the age private identity off the application server. Set the non-secret AGE_RECIPIENT public recipient and run `bash scripts/backup.sh`; it streams mysqldump into an encrypted .sql.age file with restrictive permissions. Test decryption/restoration into a separate database. Arrange an operator-owned schedule, off-host copies, retention and restore drills. No backup schedule has been installed for you.

The app uses an in-memory session store: restarting signs users out. Add shared sessions before horizontal scaling. Rate counters already coordinate through MySQL. Container logs rotate, but configure operational alerting externally. Email password recovery is available after SMTP and the reset migration are configured; see FORGOT_PASSWORD.md. MFA, SSO and automated incident response are not implemented.

If a secret leaks, revoke/rotate it at its source and update the protected file. Existing MySQL volume passwords are not changed by editing secret files; change the account password and application secret together using a planned rotation procedure.

## References used

- https://docs.docker.com/compose/how-tos/use-secrets/
- https://caddyserver.com/docs/automatic-https
- https://docs.spring.io/spring-security/reference/7.0/features/exploits/http.html
- https://dev.mysql.com/doc/refman/8.4/en/privileges-provided.html
