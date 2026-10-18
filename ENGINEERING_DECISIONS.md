# Engineering decisions

## 1. Keep a small server-rendered Java system

The project targets Java 21 with Spring Boot MVC, Thymeleaf and parameterized Spring JDBC. Bootstrap/CSS/JS are now self-hosted so a strict self-only CSP is practical and no third-party frontend request is required. Public introduction/legal pages are separate from the private queue. The design uses full-page navigation and URL filters rather than an SPA. Private pages are noindex and never enter the public sitemap; authentication remains the access boundary.

## 2. Enforce consistency and privileges in the database

Claim, update and transfer SQL include expected versions. Transfer also checks recipient team membership. Audit events and durable retry keys share the mutation transaction. Runtime credentials can insert user names/emails/hashes, but cannot set or update the role column, modify/delete audit history or perform DDL. Initial ADMIN creation uses a separate provisioning identity mounted only in a tools profile. This limits accidental privilege escalation but does not replace application authorization or a database security review. The production profile disables automatic schema initialization.

## 3. Authentication, request integrity and abuse controls

BCrypt, session fixation protection, CSRF, admin URL rules and fresh service-level database permissions remain enabled. Login attempts use shared SQL quotas keyed with HMAC rather than raw usernames/IPs. A honeypot and minimum form age add basic bot friction. Failure of the limiter fails closed. No public signup or uploads are implemented; their routes/multipart requests are rejected. Plain-text user content is escaped, not treated as trusted HTML. These controls are deliberate, but they are not MFA, CAPTCHA or a guarantee against every automated attack.

## 4. Deployment boundaries and external configuration

Docker builds without runtime secrets. The app runs non-root, read-only, with a bounded writable /tmp. The database and app have no host port mapping. Caddy terminates HTTPS and overwrites forwarded headers before a private connection to the app. Production enforces secure cookies, hidden client errors, disabled debug and required configuration. Compose mounts file secrets via Spring configtree; .env carries only non-secret deployment settings. File secrets still require filesystem permissions and encrypted host disks. Internal MySQL TLS uses REQUIRED; identity verification with a trusted CA is a further requirement for managed/multi-host setups.

## 5. Privacy and evidence-based verification

Analytics is disabled until configured and consented to. It counts only three public page names by date, without visitor IDs or workspace content; admins see aggregates. Security counter and analytics cleanup are bounded scheduled tasks. Work/audit retention stays an operator policy, not an unimplemented deletion promise. Tests cover workflow, retry behaviour, authorization, CSRF, headers, origin restrictions, throttling, generic errors and HTTPS configuration. Desktop/mobile checks and local asset/response measurements were performed, while Docker, real DB grants, public TLS and hosted load/Lighthouse checks remain pending. See TEST_REPORT.md for actual results.

## Operational trade-offs

The service is prepared for one application instance; sessions are in memory and restart signs users out. Shared sessions, MFA/SSO, safe membership revocation, versioned migrations, container vulnerability/digest checks, monitoring and a verified backup schedule remain operator/development work before relying on it for sensitive production use.
