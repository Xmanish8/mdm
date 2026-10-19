# Engineering decisions

## Server-rendered pages and JDBC

Spring MVC and Thymeleaf keep routing, validation and sessions in one Java application. Bootstrap is served locally, so the app needs no frontend build or third-party asset requests. Controllers handle requests; services check access and perform database operations with parameterized `JdbcTemplate` queries. There is no unused second repository layer.

## Team boundaries

The authenticated email identifies the user. Services read the current role and team membership from the database rather than trusting a user ID supplied by a form. Members can access only their teams' tasks. Administrators manage accounts and memberships and review approvals. A new member has no team access until membership is added.

## Competing claims and stale changes

A claim updates an open, unassigned task only when its version still matches. The update count determines whether the claim succeeded. Two simultaneous claims cannot both win.

Edits and transfers also check the expected version and increment it. A stale form returns a conflict instead of overwriting a newer change. Transfers additionally check that the recipient belongs to the task's team and require a reason. This is optimistic concurrency: users must refresh and review changes after a conflict.

## Workflow and history

The workflow distinguishes open, in-progress, blocked, pending approval, resolved and cancelled work. Members cannot resolve their own pending approvals through a direct request. Completed tasks cannot be edited. Comments and work changes record activity in the same transaction as the mutation. An audit failure rolls back the work change.

## Duplicate submissions

Create and comment forms carry a request UUID. `operation_requests` stores the actor, key, operation type, payload digest and result. Reusing a completed key with the same payload returns the prior result. Reusing it with changed data returns a conflict. The request row is locked during the transaction, so retries cannot create a second result.

## Authentication and recovery

Spring Security handles session login, CSRF and route-level roles. Passwords use BCrypt. Private requests also compare a session password fingerprint with the current database hash, invalidating sessions after a reset.

Recovery tokens contain 256 random bits; the database stores only their SHA-256 digest. The latest link replaces earlier links, expires after 15 minutes and is consumed in the password-change transaction. User-row locks serialize competing resets. Known and unknown emails receive the same acknowledgement. Delivery is queued before account lookup to keep mail latency out of the response.

The response uses `Referrer-Policy: strict-origin`: paths and query strings are omitted, but browsers retain a usable Origin on form submissions. The earlier `no-referrer` setting caused browser forms to send `Origin: null`, conflicting with exact origin checks. CSRF and foreign/opaque-origin rejection remain enabled.

## Abuse controls and deployment

The optional reviewer role panel uses four dedicated sample accounts and normal form authentication. Enabling it requires the separate `mdm_review` database, restricted `mdm_review_app` identity, a sample-only marker and matching account roles/passwords/memberships. Only the public demo password is displayed after these checks. The production ban on automatic demo seeding remains in place; review setup is an explicit one-time provisioning step. Review changes persist and there is no automatic destructive reset.

Shared SQL counters limit login and reset attempts. Counter keys use HMAC instead of storing raw account/IP combinations. A honeypot and minimum form age add basic bot checks. Failure of the limiter rejects the request. Thymeleaf escapes user content; uploads and public signup are disabled.

Production uses a separate configuration profile, restricted database credentials and a non-root container. Only Caddy publishes ports; application and database networks remain private. Runtime credentials cannot change roles, alter schema or overwrite audit history. Initial administrator creation uses a separate provisioning identity. Deployment secrets are mounted from excluded files.

## Limits and next steps

- Sessions and the bounded mail queue are in memory. Shared sessions and a durable mail outbox are needed for multiple instances and reliable delivery across restarts.
- MySQL TLS uses `REQUIRED` in the single-host Compose setup. Managed or multi-host deployment should use a trusted CA and `VERIFY_IDENTITY`.
- Membership revocation, MFA/SSO, versioned migration tooling and monitoring are not implemented.
- Real SMTP delivery, MySQL integration tests, Docker execution, public TLS and backup restoration require deployment verification. Local tests do not establish hosted performance or a security audit.
