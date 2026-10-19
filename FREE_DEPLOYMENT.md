**MDM: free deployment on Render + Aiven MySQL**

Prepared for the existing project at `E:\projects\MDM-production`, checked 19 October 2026. This is an add-on to that project, not a replacement project. No cloud resources, real credentials or certificates are included. The Docker image and live cloud connection still need testing after you configure your own accounts.

**1. Understand what you are deploying**

| Component | Location |
|---|---|
| Java 21 / Spring Boot / Spring Security | Render Free Web Service |
| Thymeleaf, Bootstrap, CSS, JavaScript | Same Spring Boot service |
| MySQL records, users, audit history | Aiven Free MySQL |
| Source | Your existing GitHub repository |
| HTTPS and website address | Render's generated onrender.com address |

Choose the actual Free compute/database plans, not paid plans running on trial credit. Render Free sleeps after 15 minutes without traffic and can take about a minute to wake. Its filesystem is temporary. Aiven Free has 1 GB disk storage, no fixed expiry and can be powered off for inactivity. This is an assignment/demo deployment, without an always-on production guarantee. [Render limits](https://render.com/docs/free), [Aiven limits](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier).

You do not need a separate frontend host, paid domain, VPS, Caddy, Redis, object storage or local Docker installation for this route. Your current app does not support file uploads. Password-reset email remains disabled initially; step 15 explains why.

**2. Put this add-on inside your existing project**

Extract the add-on ZIP to a temporary folder. Copy its contents into `E:\projects\MDM-production`, merging the `deploy` and `database` folders. It adds:

```text
FREE_DEPLOYMENT.md
deploy/render/app-free.Dockerfile
deploy/aiven/README.txt
database/aiven-runtime-grants.sql
```

Do not extract an old full-project ZIP over your working project. This add-on leaves your existing Dockerfiles, Java source and local database unchanged.

Open PowerShell:

```powershell
Set-Location 'E:\projects\MDM-production'
java -version
.\mvnw.cmd package
```

Java 21 or later is needed locally; the Docker image uses Java 21. Continue after BUILD SUCCESS. Database integration tests require separate opt-in/configuration; skipped tests do not prove the cloud database works. The image build intentionally skips tests, so run this local check before pushing changes.

**3. Prepare your private login and secret files**

In an interactive PowerShell window:

```powershell
.\scripts\prepare-render.ps1
```

If script execution is blocked, run this one process:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-render.ps1
```

The helper prompts for your name (default Manish Marathe), a real email address and a NEW website login password. Use at least 12 characters. Password entry is hidden. This email/password becomes your first website administrator login; it is different from your MySQL credentials and your Render/Aiven account logins.

The helper creates private files in `.secrets`:

| File | Use for this free route |
|---|---|
| `db.password` | Password for the cloud `mdm_app` MySQL account |
| `rate.secret` | Render app's rate-limit signing secret |
| `render-bootstrap.sql` | Insert first website administrator into the NEW cloud database |
| `root.password`, `provision.password` | Not used on Aiven; do not upload them to Render |

If it reports that `render-bootstrap.sql` already exists, preserve it. Use your previously chosen email/password and existing files. Do not overwrite them to reset an account. If the matching login password is unknown, stop before importing that administrator and arrange a fresh initialization file for a fresh cloud database.

The helper needs Spring Security's cached dependency. If missing, run the Maven package command first. If you intentionally use a custom Maven cache, pass `-MavenRepository 'YOUR_ACTUAL_CACHE_PATH'`.

**4. Create Aiven Free MySQL**

1. Open [Aiven Console](https://console.aiven.io/) and create/sign into your account.
2. Create/select a project, then create an **Aiven for MySQL** service.
3. Explicitly select the **Free** plan. Review the displayed cost before creating it. Region choices depend on availability; choose one near the Render region when possible.
4. Wait until the service is running.
5. Open Overview / Connection information and privately record **host, port, username and password**. The initial management username is normally `avnadmin`; use what your service actually displays.
6. Under Connect > Databases, create `mdm`. Keep the existing default database intact.
7. Download the service's **public CA certificate**. Save it as:

```text
E:\projects\MDM-production\deploy\aiven\ca.pem
```

Make sure Windows has not named it `ca.pem.txt`. It should start with `-----BEGIN CERTIFICATE-----`. This is a public trust certificate and can be included in your source. It is not a private key or password. Only download it from your own Aiven service. The added Dockerfile builds a Java truststore from it. [Aiven certificate details](https://aiven.io/docs/platform/concepts/tls-ssl-certificates).

**5. Connect MySQL Workbench to Aiven**

1. Open Workbench and add a new connection, e.g. `MDM Aiven Admin`.
2. Connection method: Standard TCP/IP.
3. Hostname: Aiven host, WITHOUT `https://`.
4. Port: exact Aiven port, not an assumed 3306.
5. Username/password: Aiven's management credentials.
6. Open SSL settings, select **Require and Verify Identity** (or the equivalent identity-verifying option), and select your downloaded `ca.pem` as the CA file. No client key/certificate is needed for normal password authentication.
7. Test Connection. Stop and correct any certificate, hostname or password error instead of disabling verification.
8. Connect, select `mdm` in SCHEMAS and make it the default schema.

If you configure Aiven's IP allowlist now, include your computer's PUBLIC IP as `/32`. Your computer's `192.168...` address is not the public address. Do not remove your current access until you have added the correct replacement. [Workbench connection instructions](https://aiven.io/docs/products/mysql/howto/connect-from-mysql-workbench).

**6. Import the schema into the NEW cloud database**

In Workbench, run:

```sql
USE mdm;
SELECT DATABASE();
```

The result must be `mdm`. Open and execute the entire project file:

```text
E:\projects\MDM-production\src\main\resources\schema.sql
```

It creates missing tables without dropping data. It is a fresh-install schema, not a migration engine that repairs all differences in old tables. For this walkthrough use a newly created cloud database. If your cloud database already contains records, take a backup and compare its schema before importing anything.

Verify:

```sql
USE mdm;
SHOW TABLES;
```

The current schema creates 10 tables: `users`, `teams`, `team_memberships`, `work_items`, `activity_logs`, `operation_requests`, `admin_activity`, `auth_rate_limits`, `analytics_daily`, `password_reset_tokens`. The older connection test checks five core tables within this larger set.

Your local users/tasks are NOT automatically copied. This walkthrough starts with clean cloud records. If you need local data, export/import it separately with backups and review; do not combine an existing users import with a duplicate administrator bootstrap.

**7. Create your first WEBSITE administrator**

Still connected as the Aiven management user, with `mdm` selected, open this PRIVATE file in Workbench:

```text
E:\projects\MDM-production\.secrets\render-bootstrap.sql
```

Execute it once. It inserts your chosen name, normalized email, BCrypt password hash and ADMIN role. Do not paste it into chat, GitHub or Render environment variables. Verify without selecting the password hash:

```sql
SELECT id, name, email, role FROM mdm.users;
```

You should see Manish Marathe (or your entered name), your real email and ADMIN. A duplicate-email error means the row already exists; inspect it instead of repeatedly importing or deleting records.

**8. Create the restricted WEBSITE database account**

Open `database\aiven-runtime-grants.sql` in Workbench as a new query. Copy the contents of `.secrets\db.password` into the password placeholder in that PRIVATE query tab. Keep the generated hex password unchanged, with no added spaces/newlines. Do not save a real password back into the tracked SQL file.

Execute the CREATE USER statement first. If `mdm_app` already exists, STOP and inspect it; do not blindly reuse an existing account. For a newly created account, execute the remaining GRANT statements and SHOW GRANTS.

The script permits the app's required reads/writes but does not grant schema changes, database administration, role changes or audit-row modification. It requires encrypted connections. Do not give this account `ALL PRIVILEGES` to resolve a denied query. Use the exact missing permission/error to diagnose it. Aiven's console-created users can default to broad privileges, which is why this script creates a dedicated account with explicit grants. [Aiven service users](https://aiven.io/docs/products/mysql/howto/manage-service-users).

The Aiven management password stays with you; only `mdm_app`'s password goes to Render. Close the private query tab without saving its real password into the project.

**9. Commit and push the deployment additions**

Your current repository already has an `origin` remote and uses `main`. Do not initialize another repository. In PowerShell:

```powershell
Set-Location 'E:\projects\MDM-production'
git status --short
git check-ignore .secrets/db.password .secrets/rate.secret .secrets/render-bootstrap.sql
git ls-files .secrets .smtp-secret .env
```

The ignore check should list the three private files. The tracked-files check should produce no output. If a real secret is already tracked, stop, remove it from tracking and rotate the exposed credential; adding .gitignore does not remove it from history.

Stage only the additions and PUBLIC CA:

```powershell
git add FREE_DEPLOYMENT.md deploy/render/app-free.Dockerfile deploy/aiven/README.txt deploy/aiven/ca.pem database/aiven-runtime-grants.sql
git diff --cached --stat
git diff --cached
git commit -m "Add free Render deployment with verified Aiven MySQL"
git push origin main
```

Review the staged text before committing. If there are unrelated pending changes, review/commit them separately. Confirm GitHub has `application-render.properties`, `app-free.Dockerfile` and the public `ca.pem`. Render will use the pushed source, not changes that exist only on your computer.

**10. Create Render Free Web Service**

1. Open [Render Dashboard](https://dashboard.render.com/) and sign in.
2. Select New > Web Service.
3. Connect your existing GitHub repository through Git Provider.
4. Configure the following settings:

| Field | Value |
|---|---|
| Name | A unique name such as `mdm-manish` if available |
| Branch | `main` |
| Language/runtime | Docker |
| Root directory | Blank: pom.xml is at repository root |
| Dockerfile path | `./deploy/render/app-free.Dockerfile` |
| Docker build context | `.` |
| Docker command/start override | Blank |
| Instance type | **Free** |
| Region | Near your Aiven service if available |
| Health check | `/welcome` |

Do not create the previous paid Render MySQL private service, attach a disk or deploy Compose. This free route runs only the app on Render. The Dockerfile contains its build and startup commands. [Render Web Services](https://render.com/docs/web-services).

**11. Enter Render environment variables**

Under Environment, add:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod,render` |
| `SPRING_DATASOURCE_USERNAME` | `mdm_app` |
| `SPRING_DATASOURCE_URL` | JDBC URL below, replacing the host and port |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `3` |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `1` |
| `SERVER_TOMCAT_THREADS_MAX` | `50` |
| `LEGAL_OPERATOR` | `Manish Marathe` or your actual operator name |
| `LEGAL_CONTACT` | Your real contact email |
| `ANALYTICS_ENABLED` | `false` |
| `MDM_RESET_EMAIL_ENABLED` | `false` |

Use this JDBC URL as ONE LINE, without quotes:

```text
jdbc:mysql://YOUR_AIVEN_HOST:YOUR_AIVEN_PORT/mdm?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false&trustCertificateKeyStoreUrl=file:/opt/app/aiven-truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit&fallbackToSystemTrustStore=false
```

Replace only `YOUR_AIVEN_HOST` and `YOUR_AIVEN_PORT`. Do not put your MySQL password in this URL. The truststore contains only a public CA; `changeit` protects that public-certificate container and is not a credential for any account. The CA is packaged by app-free.Dockerfile. [Connector/J server authentication](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html).

Leave `SITE_BASE_URL` unset initially: the existing render profile reads Render's generated HTTPS external URL automatically. Do not set it to localhost or append `/login`. Do not manually define `RENDER_EXTERNAL_URL`. With a custom domain later, set SITE_BASE_URL to the exact HTTPS origin you use for login.

Under **Secret Files**, add two files:

| Filename | Contents |
|---|---|
| `db.password` | Exact contents of local `.secrets\db.password` used in step 8 |
| `rate.secret` | Exact contents of local `.secrets\rate.secret` |

Use the filename only, with no path, quotes or `KEY=` prefix. Render mounts these at `/etc/secrets/`; the existing profile reads them. Do not upload the bootstrap SQL, root password or provisioner password to the web service. Never add secrets as Docker ARG/COPY instructions. [Render secret configuration](https://render.com/docs/configure-environment-variables), [Docker secret mounts](https://render.com/docs/docker-secrets).

**12. Restrict the database network and deploy**

After creating the Render service, open its **Connect > Outbound** tab and copy ALL listed outbound CIDR ranges. In Aiven, open the service's **Service settings > Cloud and network > IP address allowlist**:

1. Add every Render outbound range.
2. Keep your computer's current PUBLIC IP `/32` if you need Workbench administration.
3. Remove the broad `0.0.0.0/0` rule after correct replacement entries are present.
4. Save. Keep a way to update the allowlist if your home/public IP changes.

These Render ranges are shared region ranges, not dedicated addresses. Restricted credentials and verified TLS remain necessary. Changing Render's region can require updating the allowlist. [Render outbound ranges](https://render.com/docs/outbound-ip-addresses), [Aiven allowlist instructions](https://aiven.io/docs/platform/howto/restrict-access).

If Render already attempted its first deployment before the allowlist was ready, save all settings and run Manual Deploy > Deploy latest commit again. Watch build logs and startup logs. The Docker build needs no database connection. A missing `ca.pem` fails the build; a bad database connection normally fails when the app first queries it.

Success indicators: the build completes, logs show active `prod` and `render` profiles, Tomcat listens on the platform port, startup completes, and `/welcome` responds. The welcome health check does NOT prove MySQL works; login and creating a record do.

**13. Sign in and add useful demonstration data**

Open the exact HTTPS address shown by Render, then `/login`. Sign in with the REAL EMAIL and WEBSITE PASSWORD chosen in step 3. Do not use `avnadmin`, `mdm_app`, your database password or old local demo credentials.

Use the admin page `/admin` to create Operations and Engineering teams, member accounts with distinct passwords, and team memberships. Create several realistic tasks with different priorities. Use only sample data, not real employee/customer information. Create reviewer member credentials through the admin interface and share them privately. Do not enable demo seeding in production.

**14. Complete these live checks before submission**

| Check | Expected result |
|---|---|
| Open public address | HTTPS works; welcome and login load |
| Correct/incorrect login | Correct succeeds; incorrect stays signed out |
| Member visits `/admin` | Access denied |
| Different team member opens a private task | Access denied/no data disclosure |
| Create/claim/update task | Saved changes and visible activity history |
| Open same task in two tabs | Stale update rejected without silent overwrite |
| Submit task to admin | Approval/return workflow works |
| Repeated form submission | No duplicate task/comment |
| Logout, then authenticated page | Requires login |
| Restart/redeploy Render | Aiven records remain; signing in again may be necessary |
| Mobile width | Navigation/forms readable without horizontal scrolling |
| Privacy, terms, invalid page | Legal pages load; signed-in invalid URL shows custom error |
| Repeated failed sign-ins | Throttling activates; wait for the window before retrying |

After deployment, in Workbench reconnect as `mdm_app`, select `mdm`, and run `SHOW GRANTS;` plus `SHOW SESSION STATUS LIKE 'Ssl_cipher';`. Confirm restricted grants and a nonempty TLS cipher. Reconnect as the management user for later administration.

The free image caps Java heap at 256 MB to leave room for native JVM memory. This is a starting configuration, not a load-test result. Monitor memory and restart logs. If light use still exceeds the free allocation, reduce workload or use paid compute rather than claiming production capacity.

**15. Password recovery and other optional services**

Render Free blocks outbound SMTP ports 25, 465 and 587. Your current reset feature uses SMTP, so keep `MDM_RESET_EMAIL_ENABLED=false` for this deployment. Configuring Gmail SMTP on port 587 will not solve that limitation. A public forgot-password page is not proof that email is delivered.

If password recovery is required for evaluation, it remains unfinished on this initial free setup: implement and test a provider's HTTPS email API, or select another verified compatible email/hosting arrangement. This add-on does not implement an email API or send messages. Do not expose reset tokens in logs/pages as a workaround. [Render SMTP restrictions](https://render.com/docs/free).

Optional analytics are already built into MDM and can remain off. No external analytics account is required. A custom domain is optional; Render's generated HTTPS address is enough for submission. No additional storage host is needed until you implement file uploads.

**16. Backups, changes and ongoing operation**

Aiven Free lists backups, but you should still export an independent copy and test a restore before relying on it. For a small assignment dataset, use Workbench Data Export as the Aiven management user, choose `mdm`, export schema + data to a PRIVATE folder outside the Git repository, and check the export completed. If your client offers export-lock/GTID/tablespace options and Aiven rejects them, use a compatible MySQL client and the precise managed-service-safe options for that error; do not grant elevated permissions to the web app.

Test restoration into a separate temporary database, inspect row counts and relationships, then remove the temporary database only after confirming it is disposable. Protect exports because they contain email addresses and password hashes. Do not commit or submit database dumps. The project's VPS `scripts/backup.sh` is not an Aiven backup procedure.

Before a demo, open the public URL early enough for the app to wake naturally. Do not use artificial keep-alive traffic to evade free-plan restrictions. Monitor service quotas and Aiven inactivity notices. For updates, run local tests, review changes, commit and push. Production SQL initialization stays `never`; schema changes need a separate reviewed migration with backup. If Aiven rotates its CA, download the new public CA and rebuild/redeploy the app image.

**17. Common errors**

| Error | What to check |
|---|---|
| `ca.pem` missing during COPY | Exact certificate path/name, Git commit and selected branch |
| PKIX/certificate/hostname error | CA belongs to this Aiven service; original hostname, truststore path, current CA |
| Access denied for `mdm_app` | Exact generated password, username, account grants; no newline/quotes in secret file |
| Connection timeout/refused | Aiven running; actual port; allowlist includes all Render outbound ranges |
| `Connection attempt exceeded defined timeout` during `afterHandshake` | Render profile allows 60 seconds for pool/login setup; replace any `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=5000` override with `60000`, then redeploy. Keep verified TLS enabled. |
| Table does not exist | Imported full schema into `mdm`; JDBC URL ends with `/mdm` before query parameters |
| Permission denied on SQL | Compare required grants; do not use administrator credentials as a shortcut |
| ProductionGuard startup rejection | Both profiles, real legal email, secret lengths, HTTPS external URL |
| `db.password` or `rate.secret` unresolved | Render Secret Files exact filenames and app-free Dockerfile selected |
| Login 403/site address mismatch | Use one exact HTTPS origin; SITE_BASE_URL unset for generated URL or exact custom origin; reopen fresh login form |
| Repeated HTTPS redirects | Both profiles, preserved forwarded-header configuration, no conflicting proxy settings |
| Slow initial load | Free service waking; then check actual logs if it does not finish |
| Exit 137/OOM | Memory allocation/logs and concurrency; free hosting has finite resources |
| Password-reset mail absent | Disabled/SMTP blocked on this route; step 15 |

If asking for debugging help, share the error text and setting NAMES, with password/token values removed.

**18. Submit**

Include your GitHub repository URL, the actual live HTTPS URL, screenshots of login/dashboard/admin/workflow, setup instructions and an accurate test summary. Share reviewer credentials privately only through the assignment's accepted channel. Note the cold start and password-email limitation honestly. Include no `.env`, `.secrets`, SMTP credentials, database dump or real private records.

Completion means the PUBLIC app can sign in against Aiven, persist tasks across a Render restart, enforce member/admin/team permissions and complete the main task workflow. Merely seeing Render's green deployment status is not enough.
