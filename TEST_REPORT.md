# Testing

Latest local result (19 October 2026): Maven `package` succeeded; 104 tests discovered, 94 passed, 10 MySQL-dependent tests skipped, zero failures or errors. Render profile checks cover the platform port/URL, production settings, mounted-secret path and forwarded HTTPS. The private administrator setup helper also passed its self-check without creating a secret file. Docker images have not been built here. A public Render sign-in attempt reproduced HTTP 503; the new diagnostic change still needs deployment before its live cause can be identified.

All 13 production-security tests passed. Database failures in request throttling still return a generic 503 and prevent authentication. Server logs now record only the operation, validated SQL state, numeric vendor code and exception class chain. Tests cover a denied database login, a reset-link TLS failure and malformed SQL-state text, and verify that exception messages, reset tokens and test credentials are not logged.

## Run the default suite

```powershell
.\mvnw.cmd test
```

The default suite does not require a live MySQL database. Web tests exercise controllers, templates, authentication, CSRF, team permissions, validation, headers, throttling and error handling. Service tests check transitions, stale writes, duplicate keys and reassignment rules.

H2 tests use real transactions and row locks for password reset expiry, replacement, single use, rollback after audit failure and concurrent redemption. Demo-account tests check Indian sample profiles, password preservation, stable user IDs and safe handling of customized/conflicting accounts. A loopback HTTP test exercises form login, cookies and the dashboard using an isolated database and stubbed work data.

## MySQL integration tests

Use a separate test database and an account allowed to create tables and clean up test rows. Never point these tests at the submission demo database or production data.

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/mdm_test'
$env:DB_USER='your_test_database_user'
$env:MDM_DB_TESTS='true'
.\mvnw.cmd test
Remove-Item Env:MDM_DB_TESTS
```

Set `DB_PASSWORD` privately in the terminal or IDE environment before running. The integration suite checks schema/context startup, claims, stale updates, permissions, duplicate submissions and password-reset behaviour on MySQL.

## Verification limits

Checks were run on Java 25 with the source compiled for Java 21. Java 21 is configured in GitHub Actions. Ten MySQL-dependent tests are skipped when the opt-in is absent. H2 results do not prove MySQL behaviour. Mail tests use a mocked sender; a real SMTP delivery test is still required. Docker/public HTTPS, restricted production grants and backup restoration have not been executed in this workspace.

The source checker scans selected current files for common credential patterns. It does not audit Git history or dependency vulnerabilities.

```powershell
python scripts/check-source.py
```
