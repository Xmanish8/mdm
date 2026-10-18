# Forgot password setup

The login page now includes Forgot password? and links to /forgot-password. Choose and configure an SMTP provider before enabling email recovery. Existing demo addresses ending in .local cannot receive Internet email; use a real account email or a private local SMTP test inbox for development.

## Local app started with start.ps1

The option is visible immediately, but Send reset link is disabled until mail is enabled. In the same PowerShell terminal used to start MDM, set the following non-secret settings to your provider's actual values:

    $env:MDM_RESET_EMAIL_ENABLED='true'
    $env:SMTP_HOST='your-provider-smtp-host'
    $env:SMTP_PORT='587'
    $env:SMTP_USERNAME='your-provider-username'
    $env:SMTP_FROM='your-verified-sender-address'
    $env:SITE_BASE_URL='http://localhost:8080'
    powershell -ExecutionPolicy Bypass -File .\scripts\set-smtp-secret.ps1
    powershell -ExecutionPolicy Bypass -File .\start.ps1

The helper prompts privately for the SMTP password/provider app password and saves it in .smtp-secret/smtp.password. That directory is Git/Docker ignored and imported only as server configuration. Do not paste passwords into code, chat or JavaScript. .env is not automatically loaded by a standalone Java process; the environment settings above are intentional.

Port 587 defaults to authenticated STARTTLS with certificate/hostname verification. If your provider specifies implicit TLS on 465, set SMTP_PORT=465, SMTP_SSL_ENABLED=true and SMTP_STARTTLS_ENABLED=false. Keep certificate verification enabled. For a deliberately local test SMTP inbox only, SMTP_AUTH=false and SMTP_STARTTLS_ENABLED=false may be used in development; the production guard rejects unencrypted SMTP when reset delivery is enabled.

On next development startup, CREATE TABLE IF NOT EXISTS adds password_reset_tokens without dropping existing data. Restarting the updated app will require already signed-in users to sign in again because legacy sessions have no credential version.

## Existing Docker production database

Back up the database, then apply database/migrations/20261018-password-reset.sql to the actual mdm database using an authorized database administrator. Apply deploy/password-reset-grants.sql afterward. These scripts have been prepared, not executed against your live database. The app requires SELECT/INSERT/DELETE on password_reset_tokens and UPDATE of only users.password_hash. Runtime role updates remain forbidden. New empty volumes receive these grants from the updated initialization script automatically; existing volumes do not rerun initialization scripts.

Fill SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_FROM and TLS options in the non-secret .env settings. Save the password with:

    powershell -ExecutionPolicy Bypass -File .\scripts\set-smtp-secret.ps1 -Docker

On Linux, protect .secrets/smtp.password with owner UID 10001 and mode 0400, like the existing application secrets. Build and start using both Compose files:

    docker compose -f compose.yaml -f compose.smtp.yaml build app
    docker compose -f compose.yaml -f compose.smtp.yaml up -d app

The SMTP override mounts only the SMTP password into the app and adds an outbound network for mail delivery. The app/database still have no published ports. The override enables reset email; base Compose alone leaves recovery delivery disabled. Use both files consistently when changing the mail-enabled deployment. Configure SITE_BASE_URL to the real HTTPS domain before sending links.

## Behaviour

- Valid known/unknown email requests receive the same acknowledgement. Requests are queued before account lookup, keeping mail/lookup latency out of the HTTP response.
- Requests require CSRF and basic bot checks. Limits are 5 per IP per 15 minutes and 3 per normalized email per hour. Reset-page inspection and submissions are also throttled.
- The email contains a 256-bit random URL-safe token. Only its SHA-256 digest is stored. The latest issued link replaces older links and expires after 15 minutes.
- Opening a link does not consume it. The token moves into the server-side session and the browser is redirected to /reset-password without the token in the address bar. The page has no-referrer/noindex protections and no analytics.
- Password submission validates confirmation and the 12-character/72-UTF-8-byte limits. User-row locking and a token recheck serialize competing resets. Password change, token deletion and audit entry commit together.
- Passwords are BCrypt-hashed; no plaintext password or token is logged. A completion notification is queued after success. The user must sign in again; existing private-page sessions are rejected when their stored password fingerprint no longer matches the database.
- Expired/used links display Request a new link. SMTP send failure revokes the undelivered token and does not change the password. Password-reset requests never lock the account's existing password.

## Operational limits and verification

Mail work uses a bounded in-memory queue, not a durable outbox. A restart, full queue or provider failure can lose delivery; the user can request a new link after the relevant limit. Mail failure must be monitored by the operator. Request limits reduce abuse but do not provide a CAPTCHA or a complete bot-detection service.

Unit/web tests cover hashing/expiry handling, invalid/used tokens, retry validation, email failure, token-free rendered forms, CSRF/throttling, session expiry and mail configuration. Opt-in MySQL tests cover actual expiry, newest-link semantics and concurrent redemption. These actual database tests and real SMTP delivery require credentials and have not been run by the assistant. No real email has been sent or user password changed during development.
