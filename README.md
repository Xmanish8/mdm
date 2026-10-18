# MDM — Team Task Management

Manish Marathe

MDM is a web application for managing work across teams. Members can create and claim tasks, record updates, transfer responsibility and submit completed work for approval. Administrators manage teams, access and approvals.

## Stack

Java 21, Spring Boot 4.1.1, Spring MVC, Spring JDBC, MySQL, Thymeleaf, Bootstrap and Spring Security. Tests use JUnit, Spring Boot Test, MockMvc, Mockito and H2. There is no separate frontend build.

## Run locally

Install JDK 21 or later and MySQL. Create an empty database for a new installation:

```sql
CREATE DATABASE mdm CHARACTER SET utf8mb4;
```

In PowerShell, from this directory:

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/mdm'
$env:DB_USER='root'
.\start.ps1 -Demo
```

The script asks privately for the MySQL password and a demo password of at least 12 characters. Open [localhost:8080/login](http://localhost:8080/login).

| Name | Login email | Access |
|---|---|---|
| Manish Marathe | manish@mdm.local | Administrator |
| Aditi Sharma | aditi@mdm.local | Operations |
| Rahul Deshmukh | rahul@mdm.local | Operations |
| Sneha Patil | sneha@mdm.local | Engineering |

New demo accounts use the password entered at startup. Existing accounts keep their password. On the first updated demo startup, the original sample profiles are renamed in place; account IDs, passwords and work references are preserved. A customized profile or an email conflict is left unchanged. The `.local` addresses are for demonstrations and cannot receive Internet email.

Without `-Demo`, startup creates missing tables but does not create sample accounts. To continue using an existing installation, keep its `DB_URL`; when unset, the local default is the original `opsflow` database. Do not change the URL to an empty database if you want to keep seeing existing work.

Local Java startup reads environment variables, not `.env`. No password is stored in application properties. Use a separate least-privilege database account for a deployed installation.

## Main workflows

- Create a work item in one of your teams, then claim it to become its owner.
- Update its priority, next action and status. Submit it for approval when ready; an administrator can approve or return it.
- Reassign active or blocked work to another member of the same team, recording a reason.
- Add comments and review the activity history on the task page.
- Use Administration to create teams and member accounts and grant team membership.

Members can access only their teams' work. The queue has search, filters, status counts and pagination. Comments are plain text. Public registration and file uploads are not supported.

## Correctness

Claiming uses a conditional update so only one person succeeds. Updates and transfers include a version check to reject stale changes. Create/comment requests use durable retry keys to avoid duplicate writes. Work changes and their audit events commit in one transaction. These decisions and their limitations are explained in [ENGINEERING_DECISIONS.md](ENGINEERING_DECISIONS.md).

## Tests and build

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

The default suite runs without MySQL credentials. It includes real H2 transaction tests and a real HTTP login test. MySQL integration tests require a separate test database and an explicit opt-in; see [TEST_REPORT.md](TEST_REPORT.md).

## Configuration and deployment

Password recovery requires SMTP and is disabled by default. See [FORGOT_PASSWORD.md](FORGOT_PASSWORD.md).

Docker Compose includes MySQL and a Caddy HTTPS proxy. Domain, server and mail credentials must be configured before deployment. See [DEPLOYMENT.md](DEPLOYMENT.md). Analytics is optional, disabled by default and requires browser consent. Deployment has not been verified on a public host.

For Render, use the separate [Render deployment and submission guide](RENDER_DEPLOYMENT.md). It runs the application as a Docker Web Service and MySQL as a private service with persistent storage; it does not use the VPS Compose/Caddy configuration.

Known limits include an in-memory session store and mail queue, no MFA/SSO, no membership-revocation interface and no hosted load test. Restarting the application ends active sessions. Optional mail delivery needs a configured provider; links expire after 15 minutes and can be used once.

## Submit on GitHub

Keep `src`, tests, the Maven wrapper, database scripts, deployment files and these instructions in the repository. Do not upload `target`, `.env`, secret directories, backups or editor settings. Bootstrap's license is included with its local assets.

Run the tests, review `git status`, commit the source and push it to your repository. The supplied workflow runs Java 21 tests, checks common secret patterns and builds the Docker image. Submit the repository link after checking its Actions result.
