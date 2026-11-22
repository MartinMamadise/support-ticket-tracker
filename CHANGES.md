# Change log (continuation of the original project)

## Project layout
- Moved the Maven project into `backend/`; added the Angular app in `frontend/`.
- Kept the Java package `za.co.mamadise.support_ticket_tracker` and the existing classes.
- Removed: empty stray `ticket.java`, `target/` build output, `HELP.md`, `.vscode/`, `.github/modernize/`
  (editor/upgrade-tool leftovers), empty `static/` and `templates/` folders.
- `pom.xml`: removed empty `<license/>`, `<developer/>`, `<scm>` elements; filled in name/description;
  added `h2` (test scope only).

## Problems found in the existing code, and fixes
| Problem | Fix |
|---|---|
| `createdAt` / `updatedAt` were never set (always null) | `@PrePersist` / `@PreUpdate` in `Ticket` |
| No validation; blank or arbitrary title/status/priority accepted | Bean Validation on new `TicketRequest` |
| Entity bound directly from request body: a client could POST an `id` and overwrite an existing ticket | Controller now accepts `TicketRequest`; id/timestamps cannot be supplied |
| Missing ticket on PUT/DELETE threw `NoSuchElementException` -> HTTP 500 | `TicketNotFoundException` -> 404 via `GlobalExceptionHandler` |
| `GET /{id}` 404 had an empty body | Returns a JSON error body, like every other error |
| POST returned 200, DELETE returned 200 with no body | POST -> 201 + `Location`; DELETE -> 204 |
| Repository tests ran against the live MySQL database; `shouldFindTicketsByStatus` asserts `hasSize(1)` and would fail once the dev database held any OPEN ticket | Tests use in-memory H2 (`src/test/resources/application.properties`) |
| Test context needed `DB_USERNAME`/`DB_PASSWORD` to be set | Same fix; tests need no credentials |
| DB host/port/name hard-coded | Overridable via `DB_HOST`, `DB_PORT`, `DB_NAME` (defaults unchanged) |
| `spring.jpa.show-sql=true` flooded the console | Default `false` |

## Added
- Backend: `TicketRequest`, `TicketNotFoundException`, `ApiError`, `GlobalExceptionHandler`; service/controller/integration tests.
- Frontend: complete Angular application (list with search + status filter, detail, create/edit form, delete with inline confirmation, notifications) and 33 unit tests.
- README, `.env.example`, root `.gitignore` / `.gitattributes`.

## Decisions
- (Superseded in Command 2) `status` and `priority` are now Java enums stored as strings; see `docs/AI_PROGRESS.md`.
- Allowed values: status `OPEN, IN_PROGRESS, RESOLVED, CLOSED`; priority `LOW, MEDIUM, HIGH`.
- Dev-server proxy instead of CORS configuration.

## Command 5
- Schema managed by Flyway migrations (`backend/src/main/resources/db/migration`); Hibernate `ddl-auto=validate` everywhere.
- OpenAPI document and Swagger UI via springdoc, enabled only with the `dev` profile. See `docs/AI_PROGRESS.md`.


## Command 6B - Angular login, authentication state and route protection
- New `frontend/src/app/auth/`: `AuthService`, `Login` page, `authGuard`/`guestGuard`, `authInterceptor`, `safeReturnUrl`.
- Routes: `/login` (guest only); every other route sits under `authGuard`. Header shows navigation, the signed-in user and
  "Sign out" only when signed in. Current user is loaded at startup (`provideAppInitializer`).
- Interceptor: 401 on protected `/api/` calls ends the session once and redirects to login; 403 CSRF failures are retried once
  with a fresh token. Backend not changed.

## Commands 7B–18 (2026-11-22)
Ownership/visibility rules, users and teams administration, assignment with history and workload, comments and
internal notes, activity timeline, SLA management with monitor, persistent notifications, secure attachments, dashboard
analytics, a rebuilt Angular UI (shell, dashboard, queues, ticket detail, administration, profile, notifications),
login throttling, Actuator health, Docker/Compose/nginx, GitHub Actions CI, Playwright E2E, and documentation.
Breaking API changes: `GET /api/admin/users` is paged; `TicketResponse` gained fields; new statuses/priority values
(`WAITING`, `CRITICAL`). Frontend sources were reformatted once with the project's Prettier settings.
