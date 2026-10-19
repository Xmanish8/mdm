# Reviewer role panel

The optional login panel offers Manish Marathe (Administrator), Aditi Sharma and Rahul Deshmukh (Operations Members), and Sneha Patil (Engineering Member). Role selection uses normal Spring Security login, CSRF and rate limits. Permissions come from the authenticated account, never the selected card.

It uses a separate sample database, `mdm_review`, and restricted database account, `mdm_review_app`. Your existing `mdm` database and personal administrator account remain intact. Personal accounts in `mdm` do not sign into the separate review database. The manual login form still accepts accounts belonging to the review database.

## 1. Generate the setup files

Run in your own PowerShell window:

```powershell
Set-Location 'E:\projects\MDM-production'
.\scripts\prepare-reviewer.ps1
```

If Windows blocks the script, use:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-reviewer.ps1
```

The helper generates files without connecting to MySQL:

| Private local file | Purpose |
|---|---|
| `.secrets\reviewer.password` | New PUBLIC demo password, intentionally displayed on the enabled review login page |
| `.secrets\reviewer-db.password` | PRIVATE password for the new database account; never display this on the website |
| `.secrets\reviewer-bootstrap.sql` | Private one-time initialization SQL, containing the database account password and BCrypt hashes |

Existing reviewer files are preserved. The helper does not reset passwords or modify the original `db.password` or administrator bootstrap. Run `-SelfTest` to check it without creating files. Run Maven package first if the Spring Security dependency is missing from the cache.

## 2. Create the separate Aiven workspace once

Connect from PowerShell using Aiven's MANAGEMENT password:

```powershell
mysql --user=avnadmin --password --host=mysql-mdm-manumarathe8888-83e7.j.aivencloud.com --port=16752 --ssl-mode=VERIFY_IDENTITY --ssl-ca="E:\projects\MDM-production\deploy\aiven\ca.pem" defaultdb
```

At `mysql>`, confirm this name is unused:

```sql
SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='mdm_review';
```

Continue only for **Empty set**. If it exists, inspect it; do not drop an existing database to force setup. Import the prepared file ONCE:

```sql
SOURCE E:/projects/MDM-production/.secrets/reviewer-bootstrap.sql;
```

Check the output for errors. It creates the new database, 10 application tables plus a sample-only marker, four review accounts, two teams, five memberships, eight fictional tasks and restricted database grants. No original `mdm` data is dropped or changed.

Verify:

```sql
SELECT name,email,role FROM mdm_review.users;
SELECT COUNT(*) AS sample_tasks FROM mdm_review.work_items;
SHOW GRANTS FOR 'mdm_review_app'@'%';
```

Expect four accounts and eight tasks. All four website accounts use the value in `reviewer.password`.

## 3. Push the source change

In PowerShell, review pending changes and confirm no private files are tracked:

```powershell
git status --short
git ls-files .secrets .env
```

The second command must show nothing. Then:

```powershell
git add src scripts .gitignore .dockerignore .env.example REVIEWER_SETUP.md README.md ENGINEERING_DECISIONS.md TEST_REPORT.md
git diff --cached --stat
git commit -m "Add isolated reviewer role panel"
git push origin main
```

The panel remains disabled until the settings below are applied.

## 4. Configure Render

Keep Dockerfile `./deploy/render/app-free.Dockerfile`, profiles `prod,render`, the current HTTPS SITE_BASE_URL, existing rate.secret and Aiven outbound allowlist. While editing, use Save only where available and deploy after all values and files are ready.

Set:

```text
MDM_REVIEWER_ENABLED=true
SPRING_DATASOURCE_USERNAME=mdm_review_app
MDM_RESET_EMAIL_ENABLED=false
```

Replace `SPRING_DATASOURCE_URL` with this ONE LINE:

```text
jdbc:mysql://mysql-mdm-manumarathe8888-83e7.j.aivencloud.com:16752/mdm_review?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false&trustCertificateKeyStoreUrl=file:/opt/app/aiven-truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit&fallbackToSystemTrustStore=false
```

Update Secret Files:

| Render filename | Contents |
|---|---|
| `db.password` | Replace with local `.secrets\reviewer-db.password` |
| `reviewer.password` | Add contents of local `.secrets\reviewer.password` |
| `rate.secret` | Keep existing value |

The truststore contains only the public CA; `changeit` is not an account credential. Keep database passwords out of the JDBC URL and frontend. Do not enable MDM_DEMO; review accounts are provisioned separately.

Deploy the latest commit. Startup rejects reviewer mode against any database other than `mdm_review`, any runtime user other than `mdm_review_app`, a missing sample-only marker, or mismatched reviewer roles/passwords/memberships. A disabled panel does not query reviewer accounts or publish their password.

## 5. Check the public panel

Open `/login`, select a card and click Explore. Test Aditi and Rahul for Operations access, Sneha for Engineering access, and Manish for administration and approvals. Sign out between accounts. Members must be denied `/admin` and other teams' tasks. Test an open task claim, updates/comments, reassignment and a pending approval. Check keyboard navigation and a narrow mobile screen.

Sample data is shared and persistent: reviewers can see each other's changes. There is no automatic destructive reset. Use fictional data only. Password-reset email remains disabled until a compatible delivery service is implemented/configured.

## Switch back to the private workspace

Set `MDM_REVIEWER_ENABLED=false`, restore URL path `/mdm`, set username `mdm_app`, restore Render's `db.password` from your original `.secrets\db.password`, and redeploy. Your personal administrator remains in that original database. Keep the review database unless you deliberately decide it is disposable later.

For simultaneous private and public review workspaces, deploy a separate application service against `mdm_review`; review the combined hosting quotas first. This feature does not promise two always-on free services.
