# Password recovery verification

Implemented directly in E:\projects\MDM-production on 18 October 2026.

Final Maven package result: BUILD SUCCESS. 89 tests discovered, 79 passed, 10 explicitly skipped; zero failures/errors. Source targets Java 21; checks executed on Java 25.

New coverage includes public recovery pages, generic known/unknown acknowledgements, CSRF and rate limiting, honeypot, token-free rendered forms, validation without reflecting passwords, session invalidation, mail failure revocation, SMTP production guards and BCrypt-compatible password handling.

Four default H2 database tests used real transactions and row locks: expired-token rejection, replacement/single-use behaviour, rollback of both password and token deletion when audit insertion fails, and exactly one successful concurrent redemption. These establish behaviour on the isolated H2 test database; they do not prove actual MySQL behaviour.

Ten real-MySQL-dependent tests were skipped without credentials, including three new password-reset database tests. Existing local/production MySQL schema and grants were not changed by the assistant. Development startup creates the new table; existing production volumes require the explicit table/grant migration documented in FORGOT_PASSWORD.md.

Email tests used a mocked sender. No real SMTP provider was configured, no real email was sent and no user's actual password was changed. Delivery is disabled by default. The bounded mail queue is in memory, not a durable outbox.

The real executable JAR served the new forgot-password page on a temporary loopback preview. Its mobile layout was inspected at 390px with no visible overflow; a screenshot was saved. The preview enabled the form with a placeholder SMTP host for display only; it submitted no recovery request. The temporary server was stopped afterward.

The source secret check found zero common credential patterns or literal database passwords in scanned main source/deploy files. The new SMTP secret script passed PowerShell parsing. No SMTP credentials were written, printed or bundled. Git history and dependency vulnerabilities were not audited by that source check.

The existing production safeguards continue to pass their default tests. Docker execution, real SMTP delivery, production migration/grants, real MySQL races and public certificate/network checks remain deployment verification work. Existing signed-in sessions must sign in again after this update.
