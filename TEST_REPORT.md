# Testing

Latest local result (19 October 2026): Maven `package` succeeded; 117 tests discovered, 107 passed, 10 MySQL-dependent tests skipped, zero failures or errors. Render profile checks cover the platform port/URL, production settings, mounted-secret path, bounded pool timeouts and forwarded HTTPS. RenderDataSourceTest loads the production/Render configuration through Boot's actual datasource and JdbcTemplate auto-configuration using a real Hikari/H2 pool, verifies that binding does not start the pool early, and executes SELECT 1. The two nested driver timeout properties were removed after Render reported a pool-sealed binding failure; the 60-second pool settings remain. This test does not reproduce Render's exact MySQL startup environment. The private administrator and reviewer setup helpers passed their self-checks. Docker images have not been built here. The enabled reviewer panel still needs live verification after redeployment.

All 13 production-security tests passed. Database failures in request throttling still return a generic 503 and prevent authentication. Server logs now record only the operation, validated SQL state, numeric vendor code and exception class chain. Tests cover a denied database login, a reset-link TLS failure and malformed SQL-state text, and verify that exception messages, reset tokens and test credentials are not logged.

After the pool-binding correction, a full non-web application startup attempt with the prod,render profiles and real Aiven reviewer configuration got past datasource binding to the reviewer readiness query, then failed to establish a network connection (SQLState 08S01, ConnectException) from this workspace. It made no database changes. This attempt does not verify current Render-to-Aiven connectivity or live sign-in.

Reviewer tests reject private database/account selection, a missing sample-only marker, missing or mismatched public credentials, unexpected roles and incomplete memberships. Login tests verify that reviewer credentials still require CSRF and that tampering with the selected role cannot promote a member. Disabled reviewer mode makes no reviewer database calls or credential disclosures.

Local browser checks used an isolated fictional H2 workspace: card selection changed the account, Aditi signed into Operations and was denied administrator and Engineering access, and Manish signed into administration. A test-only fixture translated MySQL's IF expression to CASE for H2 rate counters; production SQL was unchanged. The panel was checked at desktop and 390px mobile width with no horizontal overflow.

A direct verified-TLS connection to Aiven as mdm_review_app found the separate mdm_review marker and accounts but no memberships or tasks. Five missing memberships were added transactionally. Replaying the sample setup reproduced MySQL error 1267 (mixed collations); the generator now explicitly uses utf8mb4_unicode_ci for converted literals. Only the eight missing fictional tasks and eight creation-history rows were restored in a transaction after verifying the workspace was empty of tasks. All ReviewerAccess readiness checks then passed against Aiven, including the four passwords/roles and five memberships. No original mdm data, account passwords or existing tasks were modified. This direct check does not verify Render's network path or replace the ten opt-in integration tests.

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
