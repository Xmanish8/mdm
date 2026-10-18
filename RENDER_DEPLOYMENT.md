# MDM: Render deployment and assignment submission

Prepared for Manish Marathe on 19 October 2026. This guide matches `E:\projects\MDM-production` and the Render files included with it.

## What you will deploy

- **Web Service:** Spring Boot, Thymeleaf, Bootstrap and Spring Security together.
- **Private Service:** MySQL 8.4 with a persistent disk and restricted application credentials.
- **HTTPS:** supplied by Render at an `onrender.com` URL; a custom domain is optional.

Use paid services for this route. A private MySQL service and persistent disk are not an entirely free deployment. Free web instances sleep after inactivity, have no dashboard shell/one-off jobs and block outbound SMTP on 465/587. Paid services still block outbound port 25, so use your provider's encrypted 587 or 465 endpoint. Review the current instance/disk prices in the dashboard before creating anything. No Render resources or subscriptions have been created here. [Free-tier limits](https://render.com/docs/free), [pricing](https://render.com/pricing)

This is a fresh cloud database. Your laptop's database, passwords, sample users and tasks are not copied automatically. Caddy and Docker Compose are for the VPS deployment route; Render runs these two Docker services separately.

## 1. Confirm the prepared files

Your repository root must contain `pom.xml`, `src`, `scripts`, `deploy` and the Dockerfiles below. Upload source files to GitHub, not just a ZIP or the compiled JAR.

| File | Purpose |
|---|---|
| `deploy/render/app.Dockerfile` | Java 21 application image with access to Render's secret-file group |
| `deploy/render/mysql.Dockerfile` | MySQL image, schema and initialization |
| `deploy/render/02-initialize.sh` | Applies restricted grants, creates the first administrator, locks the unused provisioner |
| `src/main/resources/application-render.properties` | Render port, secret path and generated HTTPS URL |
| `scripts/prepare-render.ps1` | Prepares private passwords and the administrator initialization file |
| `scripts/RenderAdmin.java` | Hidden password entry and BCrypt hashing |
| `RENDER_DEPLOYMENT.md` | This guide |

Keep the original Dockerfile and Compose files for the other deployment route. The Render app image uses UID 10001 and supplementary group 1000. MySQL is also added to group 1000 because Render runtime secret files can require it; no password is baked into either image. [Render Docker secret access](https://render.com/docs/docker-secrets)

## 2. Run local checks and prepare private credentials

In a regular interactive PowerShell terminal:

```powershell
cd E:\projects\MDM-production
.\mvnw.cmd package
.\scripts\prepare-render.ps1
```

The helper needs JDK 21+ and the Spring Security library downloaded by Maven. If you use a custom Maven repository, pass it with `-MavenRepository 'YOUR_CACHE_PATH'`.

Enter:

- Administrator name: **Manish Marathe** (the default).
- A real administrator email address you control.
- A new production login password, at least 12 characters and at most 72 UTF-8 bytes.
- The same password again for confirmation.

The terminal hides the password. The helper generates random service passwords and creates these local, ignored files:

```text
.secrets/db.password
.secrets/root.password
.secrets/provision.password
.secrets/rate.secret
.secrets/render-bootstrap.sql
```

The SQL file contains a BCrypt password hash, not your plaintext password. Name/email/hash values use SQL hex literals to avoid quoting problems. Existing service secrets are preserved, and an existing administrator initialization file is not overwritten. Rerunning this helper does not reset a database password.

Do not post these files in chat, include them in screenshots, commit them, or upload them as public submission attachments. Store the production login password in your password manager. Open the secret files locally only when transferring their contents into Render's private Secret Files form.

## 3. Push the source to GitHub

Use your existing repository and remote if already configured. Review pending changes first:

```powershell
git status
git add .
git diff --cached --name-only
```

The staged list must not contain `.env`, `.secrets`, secret filenames, backups or `target`. If it does, stop and remove those paths from staging before committing.

Then:

```powershell
git commit -m "Prepare MDM deployment on Render"
git push
```

If the project has no GitHub remote yet, create a repository and configure its real URL before pushing. Do not invent a remote or replace an existing one. The reviewer should receive source, setup instructions, engineering decisions and tests.

## 4. Create the MySQL private service first

In the Render dashboard, choose **New → Private Service**, connect GitHub and select the MDM repository. Use these settings:

| Setting | Value |
|---|---|
| Name | `mdm-mysql`, or an available name |
| Runtime / Language | Docker |
| Branch | Your submission branch, usually `main` |
| Root Directory | Blank when `pom.xml` is at the repository root |
| Dockerfile Path | `./deploy/render/mysql.Dockerfile` |
| Docker Build Context | Repository root (`.`), if the field is shown |
| Docker Command | Leave empty |
| Region | Choose one and use the same region for the app |
| Instance | Paid; around 2 GB RAM is a starting estimate for MySQL, not a tested capacity guarantee |
| Persistent disk mount | **`/var/lib/mysql`** |
| Disk size | For example 10 GB; choose based on actual data and budget |

Do not create MySQL as a public Web Service. The private service is reached through its internal host and port. The disk path must match MySQL's data directory or data will not survive as intended. [Render MySQL setup](https://render.com/docs/deploy-mysql)

The Dockerfile supplies these non-secret database settings automatically:

```text
MYSQL_DATABASE=mdm
MYSQL_USER=mdm_app
MYSQL_ROOT_HOST=localhost
MYSQL_PASSWORD_FILE=/etc/secrets/db.password
MYSQL_ROOT_PASSWORD_FILE=/etc/secrets/root.password
```

### Add database Secret Files before the first deploy

Under the private service's Environment / Secret Files configuration, create:

| Render filename | Copy contents from this local file |
|---|---|
| `db.password` | `.secrets/db.password` |
| `root.password` | `.secrets/root.password` |
| `provision.password` | `.secrets/provision.password` |
| **`bootstrap.sql`** | **`.secrets/render-bootstrap.sql`** |

The last name intentionally differs: Render must receive the filename `bootstrap.sql`. Preserve file contents without adding extra quotes, formatting or surrounding text. Do not create plaintext `MYSQL_PASSWORD` or `MYSQL_ROOT_PASSWORD` variables in addition to the `_FILE` settings; the official image treats those pairs as conflicting.

Render mounts service secret files under `/etc/secrets/`. This database service receives root/provisioning material; the public app will not. [Render Secret Files](https://render.com/docs/configure-environment-variables)

If the creation screen does not let you add secret files until after service creation, add them before retrying the first initialization. Confirm the disk and all four secret files before deploying. Do not proceed to the app until initialization completes.

### Deploy and inspect database logs

On a new empty disk, initialization:

1. Creates the MDM schema.
2. Replaces the default broad `mdm_app` grants with the restricted grants in `deploy/02-permissions.sh`.
3. Requires TLS for the app database user.
4. Creates the administrator from `bootstrap.sql`.
5. Locks `mdm_provisioner`, which is not needed for this Render route.
6. Prints grants and the administrator count without printing passwords or hashes.

Look for successful MySQL initialization, no SQL/secret-access errors, and `administrator_accounts` equal to `1` on a fresh deployment. Keep the disk and its data. Initialization runs only on an empty disk; changing a secret file does not update an existing MySQL account.

If initialization fails, stop and inspect logs. Do not delete a disk containing data, enable broad privileges, disable TLS or retry with a different random password to hide the failure. If a brand-new empty deployment was partially initialized, review its state before deciding how to repair it.

Copy the **internal hostname and port** from the service's connection information. The hostname may include a generated suffix; do not assume it is exactly `mdm-mysql`. Use the private endpoint, not localhost or an invented `onrender.com` database URL.

## 5. Create the MDM Web Service

Choose **New → Web Service**, select the same GitHub repository and configure:

| Setting | Value |
|---|---|
| Name | `mdm-app`, or an available name |
| Runtime / Language | Docker |
| Branch | The same submission branch |
| Root Directory | Blank for the repository layout above |
| Dockerfile Path | **`./deploy/render/app.Dockerfile`** |
| Docker Build Context | Repository root (`.`), if shown |
| Build Command | None; the Dockerfile performs the Maven build |
| Docker Command | Leave empty |
| Region | Same as the private database |
| Instances | **1** |
| Instance | Paid for this guide; allow at least about 1 GB RAM initially if available, then measure actual use |
| Health Check Path | **`/welcome`** |
| Persistent disk | Not required for the app; work data is in MySQL |

Do not choose Static Site: Thymeleaf needs the Java server. Render handles HTTPS, so do not deploy the VPS Caddy configuration here. The app binds `0.0.0.0` and uses Render's `PORT`, defaulting to 10000. [Web service networking](https://render.com/docs/web-services)

### Add app environment variables

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | **`prod,render`** |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://ACTUAL_INTERNAL_HOST:3306/mdm?sslMode=REQUIRED&allowPublicKeyRetrieval=false` |
| `SPRING_DATASOURCE_USERNAME` | `mdm_app` |
| `LEGAL_OPERATOR` | Your real operator/organization; for example `Manish Marathe` if appropriate |
| `LEGAL_CONTACT` | Your real privacy/support email |
| `ANALYTICS_ENABLED` | `false` initially |
| `MDM_RESET_EMAIL_ENABLED` | `false` initially |

Replace the hostname and port in the JDBC URL with the actual connection details. Paste environment values without dotenv-style surrounding quotes. Do not use the local-profile `DB_URL` for this deployment: the production datasource is overridden by `SPRING_DATASOURCE_URL`.

Leave `SITE_BASE_URL` unset for the first deployment. The Render profile uses Render's generated `RENDER_EXTERNAL_URL`, which contains the real HTTPS service URL. When you deliberately switch to a custom domain, set `SITE_BASE_URL` to that exact HTTPS origin and consistently open that domain. Do not set a blank value or a guessed URL. [Render-generated variables](https://render.com/docs/environment-variables)

Keep `prod` active: it supplies secure cookies, hidden detailed errors, disabled debug, restricted initialization and the production guard. Do not set `MDM_DEMO=true`, enable automatic SQL initialization or connect the app as root.

### Add app Secret Files

| Render filename | Contents |
|---|---|
| `db.password` | The **same** `.secrets/db.password` used by MySQL |
| `rate.secret` | `.secrets/rate.secret` |

Do not add `root.password`, `provision.password` or `bootstrap.sql` to the public app. Do not set a separate database password environment variable that conflicts with the mounted secret. The `prod,render` profiles import files from `/etc/secrets/`.

## 6. Deploy and sign in

Click Deploy and inspect build/runtime logs. The Dockerfile builds with Java 21. Successful deployment should produce a live HTTPS URL similar to:

```text
https://YOUR-ACTUAL-SERVICE.onrender.com
```

Open `/welcome`, then `/login`. Use the real administrator email and password entered in `prepare-render.ps1`.

**`manish@mdm.local` is a local demo account, not the automatically created Render login.** The Render administrator is Manish Marathe with the real email you entered. Cloud production starts without demo teams/members/tasks. Create teams and member accounts through Administration, then grant team membership. You can use Indian display names such as Aditi Sharma, Rahul Deshmukh and Sneha Patil with appropriate account emails.

The `/welcome` health check verifies the web process, not database correctness. A successful login, dashboard and a test task are still required. Run a create → claim → update → pending approval → administrator approval walkthrough before sharing the URL.

Keep one instance: sessions and the mail queue are in memory. Deployments/restarts can sign users out; shared sessions and a durable outbox are needed before horizontal scaling.

## 7. Enable forgot-password delivery

Keep recovery disabled until you select an SMTP provider, verify its sender and check any required SPF/DKIM/domain settings. A real account email is required; `.local` addresses cannot receive Internet mail.

On Windows:

```powershell
cd E:\projects\MDM-production
.\scripts\set-smtp-secret.ps1 -Docker
```

In the **web service**, add a Secret File named `smtp.password` using `.secrets/smtp.password`. Configure the following for an authenticated STARTTLS provider on 587:

| Key | Value |
|---|---|
| `MDM_RESET_EMAIL_ENABLED` | `true` |
| `SMTP_HOST` | Actual provider SMTP host |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | Actual provider username |
| `SMTP_FROM` | Verified sender email |
| `SMTP_AUTH` | `true` |
| `SMTP_STARTTLS_ENABLED` | `true` |
| `SMTP_SSL_ENABLED` | `false` |

If your provider requires implicit TLS on 465, use 465, `SMTP_SSL_ENABLED=true` and `SMTP_STARTTLS_ENABLED=false`. Keep certificate verification enabled. Save and redeploy the web service.

Test actual inbox/spam delivery, expiry, single use, the old password failing after reset and another signed-in session being rejected. The disabled recovery button before configuration is expected. No provider credentials or real SMTP delivery have been verified here.

## 8. Checks before assignment submission

| Check | Expected |
|---|---|
| HTTPS `/welcome` and `/login` | Valid certificate, no redirect loop |
| Administrator login | Manish Marathe appears; dashboard works |
| Member login | Only authorized team work is accessible |
| `/admin` as member | Access denied |
| Another team's task URL | Access denied |
| Task creation and comments | One result per submission; user content displayed as plain text |
| Competing claims | One winner |
| Stale edit | Conflict; newer change preserved |
| Approval | Member cannot self-approve; administrator can review |
| Mobile layout and links | Usable forms; no broken navigation |
| Unknown URL | Safe custom 404 |
| Forgot password | Real delivery and one-use link if enabled; otherwise documented as unconfigured |
| Database restart/redeploy | Existing tasks persist on the disk |
| Secret exposure | No passwords in GitHub, HTML, JavaScript, screenshots or public README |

Review the real initialization grants in logs. MySQL TLS is required but currently uses `sslMode=REQUIRED`; managed/multi-host deployments should use a trusted CA and `VERIFY_IDENTITY`. Check dependencies and built images, operator/legal details, logs and resource use before ongoing production use.

The default local build passed **102 tests discovered, 92 passed and 10 MySQL-dependent tests skipped**, with zero failures/errors. The new profile tests cover Render's port, generated URL, secret path, production controls and forwarded HTTPS. The private-admin helper self-check passed without creating real credentials. Actual Render container builds/startup, MySQL initialization and live SMTP still need verification on your deployment.

## 9. What to submit

Submit these through your assignment's required channel:

1. **GitHub repository URL**, with source, Maven wrapper, setup docs, engineering decisions and tests.
2. **Live Render application URL**, normally ending in `/login` or `/welcome`.
3. **Private review-account credentials**, if the assessor needs to sign in. Create a dedicated member account/team for review and use the assessor's private credential channel; do not publish passwords in README. If administrator access is required, arrange a controlled review account rather than posting your personal administrator password publicly.
4. A few screenshots or a short walkthrough showing the dashboard, task history, reassignment and approval.
5. Current test results and known limitations. Do not claim that skipped MySQL tests, Docker/Render checks or SMTP delivery passed until you have run them.

A concise submission description can say:

> MDM is a team task-management application built with Java 21, Spring Boot MVC, Spring JDBC, MySQL, Thymeleaf, Bootstrap and Spring Security. It supports team-scoped access, ownership, reassignment, approval workflows and activity history. Conditional claims, version checks and durable request keys protect concurrent and repeated actions. Repository: [your link]. Live application: [your Render URL]. Review access: supplied privately. Verification and limitations are documented in TEST_REPORT.md and ENGINEERING_DECISIONS.md.

Replace bracketed fields with your actual links. Keep the chosen paid services running through the review period and test the live link from a separate browser before submitting.

## 10. Troubleshooting

| Symptom | Check |
|---|---|
| Dockerfile not found | GitHub contains the source; Root Directory/build context and the selected Render Dockerfile path match this guide |
| No open HTTP port | The app uses `prod,render`; `PORT` is valid; address is `0.0.0.0` |
| Could not resolve `db.password` or `rate.secret` | Correct Secret File names on the app; both profiles active |
| Permission denied under `/etc/secrets` | Use the prepared Render Dockerfiles with supplementary group 1000; never bake passwords into the image |
| Cannot connect to MySQL | Actual private hostname/port, same region, database started, correct JDBC URL |
| Access denied for `mdm_app` | Matching secret values, TLS enabled and successful initialization/grants; changed files do not rotate an existing DB password |
| Login 403/origin mismatch | Fresh login page, generated Render URL or exact configured custom HTTPS domain |
| HTTPS redirect loop | Both profiles active and forwarded HTTPS headers handled; do not disable HTTPS as a workaround |
| Login email/password incorrect | Use the real Render admin email/password, not the local demo login |
| No admin after changing `bootstrap.sql` | Existing disks do not rerun initialization; use a deliberate recovery/migration procedure |
| MySQL killed/restarts | Inspect memory/disk limits and logs; choose suitable resources without deleting data |
| Reset email unavailable | Paid app, provider/TLS settings, verified sender and `smtp.password` secret; inspect delivery logs |
| Tasks disappear after redeploy | MySQL uses the persistent disk at `/var/lib/mysql`; the app is connected to the intended database |

## 11. Remaining operational work

Render's MySQL service here is a self-hosted database container, not a fully managed MySQL product. Arrange database-aware encrypted dumps, off-platform copies, restoration tests and failure alerts. Do not rely solely on disk snapshots; Render specifically recommends tools such as `mysqldump` for database backups. The existing VPS `scripts/backup.sh` uses Docker Compose and is not directly a Render backup job. A Render-specific backup process still needs configuration.

If you need a MySQL dashboard shell/SSH session, follow Render's Docker SSH requirements: images must support an appropriate login user and `.ssh` directory. The prepared application image supports the non-root user's shell/directory requirements; the database image's root account may need an operator-reviewed SSH configuration. Do not open a public database port or weaken root authentication to bypass this. [Render SSH requirements](https://render.com/docs/ssh)

Other remaining work includes monitoring, hosted performance/load checks, actual MySQL integration tests, real email delivery, reviewed legal/retention settings and an update/rollback procedure. The application has no MFA/SSO or membership-revocation interface. These limits should be stated accurately for the assignment and addressed before sustained organizational use.
