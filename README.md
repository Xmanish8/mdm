# MDM production preparation

This package hardens the existing Spring Boot project and prepares Docker + Caddy HTTPS deployment. It has not been deployed. A real domain and server are still required. Your existing E:\projects\MDM-enhanced folder and local MySQL data were not modified.

## What changed

- Production profile: HTTPS redirects, secure/HttpOnly/SameSite cookies, cookie-only sessions, no debug/trace, no client stack traces/binding errors, cached templates, compression and bounded request/connection sizes.
- Spring Security authentication, CSRF, admin route protection and current database resource checks remain enabled. Runtime DB permissions cannot create/drop tables, modify user roles or overwrite/delete audit history.
- Shared SQL login throttling: 30 attempts per IP, 8 per account/IP and 30 per account per 15-minute fixed window. Authenticated writes are limited to 120 per identity/IP per minute. CSRF, a honeypot and form timing provide basic bot friction. No public signup exists.
- Plain-text user content is escaped by Thymeleaf. Parameterized SQL and server-side limits remain in place. File uploads/multipart are disabled, not advertised as an implemented attachment feature.
- Self-only CSP, frame denial, nosniff, origin-only referrers (no URL paths or query strings), restricted browser permissions and same-origin request checks. No wildcard CORS. Forwarded headers are overwritten at the trusted proxy.
- New public /welcome, /privacy and /terms pages; clear workspace CTA; local Bootstrap/CSS/JS; metadata, favicon, original social preview, image alt text, crawler files, custom 404/error pages and mobile styling.
- Optional first-party analytics is off by default. Consent permits daily counts of /welcome, /privacy and /terms only; no work content or visitor IDs. /admin/analytics shows aggregates. Cookies/preferences can be changed through the footer.
- .env contains deployment settings only. Random database and rate-limit secrets are created in excluded .secrets files. The initial admin password is prompted privately and hashed with BCrypt.

Read DEPLOYMENT.md for exact deployment steps, SECURITY_STATUS.md for implementation limits and TEST_REPORT.md for verification results. Do not describe this as an externally audited or already deployed production service.

## Local development

Extract into a new folder, keep your old project as a backup, start local MySQL and use `powershell -ExecutionPolicy Bypass -File .\start.ps1`. The local profile preserves your existing opsflow database and creates missing tables. Use -Demo only if you deliberately want development demo data. Production disables demo seeding and SQL initialization.

Spring Boot does not automatically load .env in local Java execution. Docker Compose reads .env and passes only declared non-secret settings. Local start.ps1 prompts for DB_PASSWORD as before.

## Test and build

Run `.\mvnw.cmd test` or `.\mvnw.cmd package` with Java 21+. The default suite does not need MySQL. Set MDM_DB_TESTS=true and DB_PASSWORD against a dedicated test database for actual MySQL checks. Keep the normal application running in one instance unless you add a shared session store; rate counters already use shared SQL.

## Production quick path

1. Choose a domain and server, install Docker Engine + Compose, and configure DNS.
2. Run scripts/setup-production.ps1 and protect the generated secret files as described in DEPLOYMENT.md.
3. Review the legal text and configure the operator/contact details.
4. Follow the staged database/build/bootstrap/start commands in DEPLOYMENT.md.
5. Verify HTTPS, restricted MySQL grants, backups/restores, login, permissions and performance on the real host.

No API keys are needed for the included functionality. Do not add provider keys to HTML, JavaScript, images, Git history, Docker build arguments or .env.example. Rotate any credential previously exposed; deleting it from current source does not revoke it.

## Forgot password
The login page now includes email-based recovery. Read FORGOT_PASSWORD.md for SMTP configuration and the existing-production database migration. Delivery stays disabled until SMTP is configured. Reset links expire after 15 minutes and are single-use; password changes invalidate existing signed-in sessions. No real SMTP credentials are included.
