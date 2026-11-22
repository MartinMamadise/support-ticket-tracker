# Security notes

This is a portfolio project, not a hardened product. The protections below are implemented and covered by automated
tests that pass in CI (backend integration tests plus a Playwright end-to-end suite), but there has been no external
review or penetration test. Do not describe it as production-secure.

## Implemented protections

| Threat | Protection | Where |
|---|---|---|
| Broken authentication | Server-side session, HttpOnly + SameSite=Lax + Secure cookies, session id changed on login, BCrypt (strength 12), password policy (10+ chars, letter + digit, ≤ 72 bytes) | `auth/SecurityConfig`, `AuthController`, `user/PasswordPolicy` |
| Credential guessing | 5 failures per username in 15 min → 429; identical message for unknown user, wrong password and disabled account | `auth/LoginAttemptLimiter` |
| Stale privileges | Account re-read on every request: disabled/deleted → session ended, role change → new authorities | `auth/SessionRoleRefreshFilter` |
| CSRF | Token cookie + header on every state-changing request, including login, logout and uploads | `SecurityConfig` |
| Vertical privilege escalation | `/api/admin/**` URL rule + `@AdminOnly`; `@StaffOnly` for assignment, teams, workload, staff lookup; only admin endpoints accept a role in a body (test-enforced); new accounts default to USER; own role and own activation cannot be changed | controllers, `RoleAssignmentRestrictionsTest` |
| IDOR / horizontal escalation | Every ticket operation and sub-resource goes through `TicketLookup` + `TicketAccessPolicy`; invisible tickets answer 404; notifications and profile are always scoped to the session's account id, never a client-supplied one | `ticket/TicketAccessPolicy`, `TicketAccessIntegrationTest` |
| Internal-note leakage | Filtered in the repository queries for comments and activity; notifications about notes go to staff only and never contain text; search does not cover comments | `comment/CommentService`, `activity/ActivityService` |
| Unsafe uploads | Extension allow-list, content must match the type (magic bytes / strict UTF-8 text), client Content-Type ignored, 5 MB limit, 20 per ticket, random storage keys (path cannot be influenced), sanitised display names, downloads as `attachment` with `nosniff` and `no-store` | `attachment/*` |
| SQL injection | JPA Criteria/JPQL with bound parameters only; `LIKE` wildcards escaped; sort fields from an allow-list | `TicketSpecifications`, `TicketListQuery` |
| XSS | Angular interpolation only (no `innerHTML`); API returns plain text; CSP `script-src 'self'` in nginx; a test asserts comment text renders as text | `ticket-detail.spec.ts`, `frontend/nginx.conf` |
| Clickjacking / sniffing | `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `X-Content-Type-Options: nosniff` | nginx, Spring Security defaults |
| CORS | Disabled by default (same origin); explicit origin list only, never `*` with credentials | `SecurityConfig` |
| Information leakage | One `ApiError` shape, generic 500 message, rejected values not echoed, no stack traces; password hashes never serialised (`UserResponse` has no such field; `toString()` of credentials-bearing objects masks them) | `error/GlobalExceptionHandler` |
| Logging | No request bodies, passwords, tokens or note text are logged; failed logins log only the exception type | services |
| Concurrent edits | Optimistic locking (`version`) | `Ticket` |
| Operational exposure | Only `/actuator/health` is exposed, without details; other actuator paths require ADMIN; Swagger UI only with the `dev` profile | `application.properties` |

## Review notes (Command 17)

Checked during this round and addressed in code: missing ownership checks on all ticket sub-resources (now central),
internal notes in activity/notifications, IDOR on notifications, enumeration through 403 vs 404, mass assignment
(DTOs only, no entity binding), privilege escalation through user creation/editing, deactivated accounts still
receiving work, upload path traversal and type spoofing, CSP compatibility of the Angular build (critical-CSS inlining
with an `onload` handler disabled), brute-force login.

## Remaining risks and limitations

- Automated tests run on H2 (MySQL mode); MySQL-specific behaviour is only exercised through the Docker stack.
- Login throttling is per username, in memory and per instance. It allows a deliberate 15-minute lockout of a known
  username and is reset by a restart. No IP-based limit (behind the proxy all clients share one address unless
  forwarded headers are configured).
- No rate limiting on other endpoints (e.g. comment or upload flooding by a signed-in user).
- Changing a password does not end the account's other sessions; there is no absolute session lifetime beyond the
  30-minute idle timeout.
- Attachments are not virus-scanned; PDFs can contain active content (they are only ever downloaded, never rendered
  inline by the app).
- The bootstrap administrator password is supplied by environment variables; change it after first sign-in.
- The Docker setup uses plain http and turns Secure cookies off; production needs TLS termination.
- No audit log of administrator actions beyond application logs and ticket activity.
