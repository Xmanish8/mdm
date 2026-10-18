# MDM deployment guide

Prepared for Manish Marathe, 19 October 2026.

This guide matches `E:\projects\MDM-production`: Spring Boot + MySQL + Docker Compose + Caddy. It describes a **fresh production database** on one Linux server. It does not move your existing local database. No server deployment, certificate issuance, SMTP delivery, production grant check or backup restoration has been performed here.

## 1. Current status and remaining work

| Item | Status |
|---|---|
| Local application and Indian demo profiles | Implemented; local use confirmed |
| Default tests | Clean build succeeded: 89 passed, 10 MySQL-dependent tests skipped |
| Production configuration, Dockerfile, Compose and HTTPS proxy | Files prepared; containers not executed here |
| Domain, DNS and Linux server | Still to select/configure |
| Production secrets and first real administrator | Still to provision |
| Secret-file access for both Java and MySQL | Must apply and check the Linux permissions in step 7 |
| Real database grants and MySQL integration tests | Still to verify |
| SMTP password recovery | Code ready; provider and real delivery test still required |
| HTTPS, public firewall and browser checks | Still to verify on the host |
| Encrypted backup, off-server copy and restoration | Still to configure and test |
| Legal identity, contact and retention wording | Operator review still required |
| Monitoring, dependency/image checks and hosted performance | Still to configure/verify |

The public site is not live yet. Working locally and passing the default tests do not establish production readiness.

## 2. Choose the server and domain

Use a Linux VPS that supports Docker Engine and persistent storage. Ubuntu 24.04 LTS is one supported option. For this single-host demo, **2 vCPU, 4 GB RAM and about 40 GB SSD** is a starting planning estimate, not a measured capacity guarantee. The build, MySQL and the application share memory; monitor them and adjust for actual use. No provider or subscription has been selected.

You need:

- A domain/subdomain you control, such as `mdm.yourdomain.com`.
- The server's public IP and an SSH account with sudo access.
- A real administrator email address and privacy/support contact.
- An SMTP provider if forgot-password delivery is required.
- A location for encrypted backups outside the server.

Allow these inbound connections in the hosting provider's firewall:

| Port | Source | Purpose |
|---|---|---|
| SSH, usually TCP 22 | Your administrator IP or trusted VPN | Server management |
| TCP 80 | Internet | HTTP redirect and certificate validation |
| TCP 443 | Internet | HTTPS |
| UDP 443 | Internet, optional | HTTP/3 |

Do not publish MySQL 3306 or application 8080. Compose already leaves these services unpublished. Docker-published ports can bypass UFW, so also check the provider firewall and actual published ports. Keep an existing SSH session open while changing access rules. [Docker firewall behaviour](https://docs.docker.com/engine/network/packet-filtering-firewalls/)

## 3. Set DNS

At your DNS provider, create an A record for the chosen hostname pointing to the server's IPv4 address. Create an AAAA record only if the server has working IPv6 and matching firewall rules. Do not leave an incorrect AAAA record in place.

From Windows PowerShell, replacing the example hostname:

```powershell
Resolve-DnsName mdm.yourdomain.com
```

The result must point to your server. Use one canonical hostname throughout. `SITE_BASE_URL` must match the exact HTTPS origin used in the browser; a different hostname or scheme will fail MDM's origin checks.

Caddy obtains and renews certificates automatically when DNS resolves correctly and public ports 80/443 are reachable. You do not need to purchase a separate certificate for this setup. [Caddy HTTPS requirements](https://caddyserver.com/docs/quick-starts/https)

## 4. Install Docker on the Linux server

The following commands are for a **fresh Ubuntu host** with no conflicting Docker/container runtime installation. For an existing server, follow Docker's prerequisite checks before changing packages. [Official Ubuntu installation](https://docs.docker.com/engine/install/ubuntu/)

Connect using your server's actual username and IP:

```powershell
ssh DEPLOY_USER@SERVER_IP
```

On the Linux server:

```bash
sudo apt update
sudo apt install -y ca-certificates curl unzip acl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker compose version
```

Use `sudo docker` consistently below. Java and a host MySQL installation are not required: the images provide them. This guide assumes ordinary rootful Docker without user-namespace remapping. Rootless/remapped installations need an adapted secret ownership plan.

## 5. Prepare configuration privately on Windows

In PowerShell:

```powershell
cd E:\projects\MDM-production
.\mvnw.cmd test
powershell -ExecutionPolicy Bypass -File .\scripts\setup-production.ps1
```

The helper asks for:

| Prompt | Enter |
|---|---|
| Domain | Your hostname without `https://`, a path or a port |
| Legal operator | Your real operator/organization, such as Manish Marathe if appropriate |
| Contact | A real privacy/support email |
| Initial administrator email | A real email you control |
| Initial administrator password | A new strong password, entered privately |

The script creates `.env` and five secret files:

```text
.secrets/
  db.password
  root.password
  provision.password
  rate.secret
  bootstrap.password
```

Database/rate secrets are generated randomly. The bootstrap password is your chosen production login password. Do not reuse a shared demo password. The script preserves existing secret files and refuses to overwrite an already configured domain; if that happens, review the existing files instead of deleting them.

Check the non-secret `.env` values in an editor:

```dotenv
SITE_DOMAIN=mdm.yourdomain.com
SITE_BASE_URL=https://mdm.yourdomain.com
LEGAL_OPERATOR="Manish Marathe"
LEGAL_CONTACT=YOUR_REAL_CONTACT_EMAIL
BOOTSTRAP_ADMIN_EMAIL=YOUR_REAL_ADMIN_EMAIL
ANALYTICS_ENABLED=false
```

Replace the placeholders with real values. Keep `.env`, `.secrets`, `.smtp-secret` and backups out of GitHub and public archives. Do not paste their passwords into chat. Local Java and Docker production use different configuration paths.

## 6. Upload the release and secrets

Use the current clean `MDM-submission.zip` supplied with this project. It contains source/configuration but deliberately excludes secrets, `.env`, `.git`, editor settings and `target`.

From Windows PowerShell, replace `DEPLOY_USER` and `SERVER_IP`:

```powershell
scp "C:\Users\Dell\Documents\Codex\2026-10-18\referenced-chatgpt-conversation-this-is-an\outputs\MDM-submission.zip" DEPLOY_USER@SERVER_IP:~/mdm-release.zip
```

On the server:

```bash
mkdir -p ~/apps
unzip ~/mdm-release.zip -d ~/apps
chmod 700 ~/apps/MDM
```

This archive has an `MDM/` top-level folder. Use a new release directory if that destination already contains a deployment; do not unzip over an active release blindly.

From Windows, transfer the configuration over SSH:

```powershell
cd E:\projects\MDM-production
scp .env DEPLOY_USER@SERVER_IP:~/apps/MDM/.env
scp -r .secrets DEPLOY_USER@SERVER_IP:~/apps/MDM/
```

Verify the SSH host fingerprint through the provider before trusting a new host. Never transfer production secrets through a public repository or public download link.

## 7. Correct and verify Linux secret permissions

On the server:

```bash
cd ~/apps/MDM
chmod 600 .env
chmod 700 .secrets
sudo chown 10001:10001 .secrets/*
sudo chmod 400 .secrets/*

sudo docker pull mysql:8.4
MYSQL_UID="$(sudo docker run --rm --entrypoint id mysql:8.4 -u mysql)"
case "$MYSQL_UID" in ''|*[!0-9]*) echo 'Could not determine MySQL UID'; exit 1;; esac

sudo setfacl -m "u:${MYSQL_UID}:r--" \
  .secrets/db.password \
  .secrets/root.password \
  .secrets/provision.password
```

**Why this matters:** the Java app/bootstrap run as UID 10001, but the MySQL initialization script runs as the image's `mysql` user. Giving every file mode 0400 owned only by 10001 can block MySQL's script. The ACL grants that specific MySQL UID read access to its three mounted files. Other host users still have no access through the owner-only directory. Do not replace this with `chmod 777`.

Compose file secrets are bind-mounted; host file permissions matter. They are not an encrypted vault. The app receives only its declared secrets, not the root password. [Compose secret permissions](https://docs.docker.com/compose/how-tos/use-secrets/)

Before first database initialization, edit `compose.yaml`. Under `services.db.environment`, add this supported MySQL setting to avoid creating a remote root account:

```yaml
      MYSQL_ROOT_HOST: localhost
```

Keep the existing database/user/password-file entries. This initialization setting does not change accounts in an existing data volume.

Normalize Linux script line endings if files passed through a Windows Git checkout:

```bash
sed -i 's/\r$//' deploy/02-permissions.sh scripts/backup.sh
```

Validate and build:

```bash
sudo docker compose config --quiet
sudo docker compose build app
```

Check readability without printing secret contents or starting MySQL:

```bash
sudo docker compose run --rm --no-deps --user "$MYSQL_UID" --entrypoint sh db -c \
  'for f in /run/secrets/db.password /run/secrets/root.password /run/secrets/provision.password; do test -r "$f" || exit 1; done; echo "MySQL secret access OK"'

sudo docker compose run --rm --no-deps --entrypoint sh app -c \
  'test -r /run/secrets/db.password && test -r /run/secrets/rate.secret && echo "Application secret access OK"'

sudo docker compose --profile tools run --rm --no-deps --entrypoint sh bootstrap -c \
  'test -r /run/secrets/db.password && test -r /run/secrets/bootstrap.password && echo "Bootstrap secret access OK"'
```

Stop and correct permissions if any check fails. Do not start initialization until all three succeed. These commands still need to be verified on your actual Docker host; Docker is unavailable in this workspace.

## 8. Initialize MySQL and the production administrator

For a fresh deployment:

```bash
sudo docker compose up -d db
sudo docker compose ps
sudo docker compose logs --tail=100 db
```

Wait for the database to be healthy and initialization to finish successfully. Then:

```bash
sudo docker compose --profile tools run --rm bootstrap
```

The bootstrap command creates one production administrator using `BOOTSTRAP_ADMIN_EMAIL` and the chosen bootstrap password. It refuses to overwrite an existing account. It is not a password-reset command.

**Production does not seed the four `.local` demo accounts.** Sign in using your real bootstrap email, not `manish@mdm.local`, unless you deliberately chose that address (which is unsuitable for real email recovery). A fresh production database starts without your laptop's teams or tasks.

Check real grants and set the production administrator's display name using the database console:

```bash
sudo docker compose exec db sh -c \
  'MYSQL_PWD="$(cat /run/secrets/root.password)" exec mysql -uroot mdm'
```

Inside MySQL, replace the email in the UPDATE with your configured admin email:

```sql
SHOW GRANTS FOR 'mdm_app'@'%';
SELECT id,name,email,role FROM users;
UPDATE users SET name='Manish Marathe' WHERE email='YOUR_REAL_ADMIN_EMAIL';
SELECT User,Host,account_locked FROM mysql.user;
ALTER USER 'mdm_provisioner'@'%' ACCOUNT LOCK;
exit
```

Confirm the UPDATE matched the intended account. The runtime user should have no DDL, role-column updates, or audit overwrite/delete permissions. It does need limited `UPDATE(password_hash)` for password recovery. Keep root access limited to the local socket. If an existing installation has a remote `root@'%'`, review and lock it only after verifying local root access.

After successful provisioning, store the bootstrap password securely in your password manager. The current Compose configuration references its secret file in the tools profile, so keep the protected file unless you also deliberately remove that bootstrap service/secret configuration. The normal app does not receive it. A locked provisioner prevents later bootstrap runs until an administrator deliberately unlocks it.

**Existing database volumes:** initialization scripts do not rerun on restart. Back up first and apply explicit reviewed migrations/grants. The password-reset table/grant scripts are included for an existing older deployment. Never delete the volume to make an initialization error disappear when it contains data.

## 9. Start the website and verify HTTPS

```bash
sudo docker compose up -d app caddy
sudo docker compose ps
sudo docker compose logs --tail=100 app caddy
```

Open your exact configured HTTPS URL in a browser. Check redirects and certificate validation without bypassing TLS:

```bash
curl -I http://mdm.yourdomain.com/welcome
curl -I https://mdm.yourdomain.com/welcome
```

HTTP should redirect to HTTPS. HTTPS should return a successful response with a valid publicly trusted certificate. If certificate issuance fails, check A/AAAA records, public 80/443 reachability, port conflicts and Caddy logs. Do not disable certificate validation.

After login, create real teams and member accounts from Administration and add memberships. Share initial member passwords privately. No public signup or automatic invitation email is implemented.

For 502 errors, inspect app/database readiness and logs. For a login origin mismatch, confirm the browser origin exactly matches `SITE_BASE_URL`, then reload a fresh login page. Do not switch between a raw IP, `www`, another subdomain and the configured hostname.

## 10. Configure forgot-password email

This is optional for an initial restricted demonstration, but required before promising users working email recovery. Choose an SMTP provider, verify its sender/domain and follow its SPF/DKIM instructions. Use DMARC according to your domain's mail policy. Confirm the VPS permits the provider's outbound SMTP port.

On Windows, save the password privately:

```powershell
cd E:\projects\MDM-production
powershell -ExecutionPolicy Bypass -File .\scripts\set-smtp-secret.ps1 -Docker
scp .secrets/smtp.password DEPLOY_USER@SERVER_IP:~/apps/MDM/.secrets/smtp.password
```

On the server:

```bash
sudo chown 10001:10001 .secrets/smtp.password
sudo chmod 400 .secrets/smtp.password
nano .env
```

Add your provider's real non-secret settings. For a provider using authenticated STARTTLS on 587:

```dotenv
SMTP_HOST=YOUR_PROVIDER_SMTP_HOST
SMTP_PORT=587
SMTP_USERNAME=YOUR_PROVIDER_USERNAME
SMTP_FROM=YOUR_VERIFIED_SENDER_EMAIL
SMTP_AUTH=true
SMTP_STARTTLS_ENABLED=true
SMTP_SSL_ENABLED=false
```

For provider-required implicit TLS on 465, use port 465, `SMTP_SSL_ENABLED=true` and `SMTP_STARTTLS_ENABLED=false`. Never disable hostname/certificate verification. The production guard rejects unencrypted SMTP when recovery is enabled.

Enable the provided override:

```bash
sudo docker compose -f compose.yaml -f compose.smtp.yaml config --quiet
sudo docker compose -f compose.yaml -f compose.smtp.yaml up -d app
```

The override enables delivery and adds the outbound mail network. **Use both Compose files consistently for subsequent mail-enabled operations**, for example:

```bash
sudo docker compose -f compose.yaml -f compose.smtp.yaml ps
sudo docker compose -f compose.yaml -f compose.smtp.yaml logs --tail=100 app
```

Test recovery using a real account email. Check inbox and spam, follow the link, reset the password, confirm the old password fails, confirm the link cannot be reused and confirm an older signed-in session must sign in again. Real delivery has not been tested here. A disabled reset button before SMTP configuration is expected.

## 11. Verify before sharing the URL

- Real admin login, logout and session expiry work over HTTPS.
- `MDMSESSION` has Secure, HttpOnly and SameSite=Lax flags.
- Anonymous users cannot open private tasks. Members cannot open `/admin` or another team's task URL.
- Claims, updates, approvals, reassignment, comments, stale-edit conflicts and duplicate-submit checks pass on the deployed MySQL database.
- MySQL integration tests pass against a dedicated test database. The 10 skipped local tests remain unverified until you run them; do not point destructive test fixtures at production.
- Production runs with `prod`, debug/trace disabled, and no client stack traces.
- Only Caddy publishes host ports. Check `sudo docker compose ps`, the provider firewall and external reachability.
- The real `mdm_app` grants match the intended limited privileges.
- Legal pages show your operator/contact and accurately describe retention, hosting and support. Review these details before publication.
- Analytics remains disabled unless enabled deliberately. If enabled, verify no page-count request before consent and that essential-only consent leaves the app usable.
- Mobile forms, links, favicon, 404 page and browser console work on the HTTPS host. Run hosted performance checks; local timings are not a hosted score.
- Scan application dependencies and final container images. Test and pin image digests for an immutable release.

Keep the first deployment restricted to your test users until these checks are complete.

## 12. Backups, restoration and monitoring

The included `scripts/backup.sh` streams a consistent MySQL dump into `age` encryption. Install age from a trusted package/source. Generate and protect the private age identity on a **separate trusted computer**, not the production server. Put only its public recipient on the server. [age installation and usage](https://github.com/FiloSottile/age)

Once age is installed and you have the real recipient:

```bash
cd ~/apps/MDM
sudo env AGE_RECIPIENT='YOUR_AGE_PUBLIC_RECIPIENT' bash scripts/backup.sh
```

The resulting `backups/mdm-<UTC timestamp>.sql.age` is encrypted and has restrictive permissions. With the sudo command it is root-owned; use an authorized administrator/off-host backup process to copy it. Do not put the private decryption identity on the server to simplify copying.

Before relying on the backup:

1. Copy an encrypted backup to your trusted restore computer.
2. Set `umask 077`, then decrypt it there with `age -d -i PATH_TO_PRIVATE_IDENTITY -o mdm-restore.sql PATH_TO_ENCRYPTED_BACKUP`.
3. Create a separate empty MySQL database, such as `mdm_restore_check`, on a test instance.
4. Import into that test database only; do not import over the running production database.
5. Compare account/task/activity counts and test login and work access in an isolated staging app.
6. Remove the temporary plaintext dump securely according to your storage policy after verification.

Configure a backup schedule, off-server retention, restore drills and failure alerts. None has been scheduled here. Protect `.env`/secrets and Caddy certificate state in your infrastructure recovery plan too; a database dump alone is not a complete server recovery plan.

Add external uptime monitoring, disk/memory checks, container restart alerts and SMTP failure monitoring. Decide who responds and how quickly. In-memory sessions and mail queues are lost on restart; do not run multiple app replicas until shared sessions and a durable mail outbox are implemented. Database TLS currently uses `REQUIRED`; managed/multi-host databases should use a trusted CA with `VERIFY_IDENTITY`.

The current app has no MFA/SSO, account-disable screen or team-membership revocation interface. An operator needs a reviewed database procedure for removing access; before relying on it for an ongoing organization, add and test a proper revocation flow. These features are not prerequisites for demonstrating the assignment, but they are remaining application work for sustained production use.

## 13. Updates and rollback

For an app-only update, first take and verify a backup, retain the previous image/release and review any schema changes. Test the new release in staging. Then build and recreate the app using the same Compose files as the active deployment:

```bash
sudo docker compose -f compose.yaml -f compose.smtp.yaml build app
sudo docker compose -f compose.yaml -f compose.smtp.yaml up -d app
```

If mail is not enabled, use only `compose.yaml` instead. Restarting signs users out. Do not assume an old image is compatible after a schema migration; plan rollback before applying database changes. Changing a password file does not rotate the password in an existing MySQL database: rotate the database account and app secret together.

Avoid `docker compose down -v` and volume-pruning commands on the deployed database. They can remove the data volume. Normal application updates do not require deleting it.

## 14. What to do next

First choose the real hostname and server. Then follow the sequence: DNS → Docker → private configuration → upload → secret access checks → database → bootstrap → app/Caddy → HTTPS checks → SMTP → backup/restore → monitored release.

The project can be submitted as source with setup instructions while public hosting is still pending, if that meets the assignment. Do not describe the website as deployed until the real URL and the checks above have succeeded.
