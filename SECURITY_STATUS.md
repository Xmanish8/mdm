# Security and product checklist

| Request | Implemented preparation | Verification / remaining scope |
|---|---|---|
| Secrets, .env and frontend keys | Blank .env settings; generated excluded file secrets; no provider keys in client assets | No real credentials bundled or rotated; use protected infrastructure storage |
| Authentication/admin access | BCrypt, sessions, CSRF, admin URL rules and current DB/resource permissions | Existing automated tests; live MySQL/MFA/SSO not verified/implemented |
| User data isolation | Team-scoped reads/writes and owner/admin edits | Default service/web tests; real DB tests opt-in |
| Lock down DB/storage | Internal network, no DB port, TLS, separate restricted/provisioning users, read-only app and encrypted backup script | Docker/grants/backup restore and host disk encryption must be checked on host |
| Production errors/debug | Debug/trace off, generic errors, startup configuration guard | Guard/HTTP tests; production environment overrides still need review |
| Input/content/SQL | Server bounds, validation, bound SQL, plain-text escaping, CSRF | Tests cover important behaviours; no rich HTML or file upload support |
| File uploads | Multipart and upload routes rejected | Rejection tests; attachments intentionally absent |
| Login/signup/bots | Shared HMAC-keyed quotas, honeypot/timing; public signup unavailable | Filter/counter tests; not a CAPTCHA or guarantee against all bots |
| Headers/CORS/HTTPS | Self-only CSP/local assets, security headers, same-origin checks, HTTPS proxy/profile | Header/redirect tests; public certificate and firewall not yet exercised |
| Privacy/terms/consent | Public operator-configured templates; essential-only option; consent-gated optional aggregate analytics | Operator legal/retention review and real deployment settings required |
| Metadata/social/favicon/alt | Public metadata, original PNG/SVG/ICO, explicit image dimensions and alt | Browser/asset checks |
| Sitemap/robots | Public pages only; private pages noindex | Automated checks; indexing policy does not replace authentication |
| Performance/mobile/contrast | Local compressed assets, template caching/compression profile, responsive layouts, improved contrast | Desktop/mobile preview; loopback report if available; hosted Lighthouse/load test pending |
| Links/custom404/forms/CTA | Public link checks, generic 404/errors, HTML+server validation, one workspace CTA | Default HTTP/template tests; authenticated E2E still needs MySQL |
| Analytics | First-party page/day totals only, disabled by default; admin aggregate view | Consent/whitelist tests; no external scripts or provider keys |

This is hardened deployment preparation, not a certification, penetration-test result, or statement that a public deployment is complete. No live database, firewall, DNS, certificate, backup schedule or GitHub repository was changed.

## Email password recovery update
Forgot password and reset pages are implemented with provider-configurable SMTP, 15-minute single-use hashed tokens, CSRF, throttling, generic acknowledgements and password-version checks on private sessions. Runtime DB permissions now allow updating only users.password_hash for this flow; role updates remain forbidden. Existing production volumes require the documented table/grant migration. SMTP delivery is disabled until configured. The in-memory mail queue is not a durable delivery guarantee; see FORGOT_PASSWORD.md.
