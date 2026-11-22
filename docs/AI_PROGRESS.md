# AI progress log

## Command 1 – Repository audit
No `docs/AI_PROGRESS.md` existed when Command 2 started, so Command 1's outcome was not recorded
here. The audit was not repeated; `CHANGES.md` was used as the record of earlier work.

## Command 2 – Backend foundation and ticket model: COMPLETE (backend not compiled or run, see below)

### Implemented
- **Enums**: `TicketStatus` (OPEN, IN_PROGRESS, RESOLVED, CLOSED) and `TicketPriority` (LOW, MEDIUM, HIGH),
  stored with `@Enumerated(STRING)`; same names as before, so existing rows and the JSON values are unchanged.
- **UTC timestamps**: `createdAt`, `updatedAt` and new `resolvedAt` are `java.time.Instant`
  (JSON: ISO-8601 with `Z`). `hibernate.jdbc.time_zone=UTC` added to main and test config.
  A `Clock` bean (`TimeConfig`) is injected into the service for testable time.
- **Resolution tracking**: `Ticket.changeStatus(status, now)` sets `resolvedAt` on the first move into
  RESOLVED/CLOSED, keeps it for RESOLVED -> CLOSED, and clears it when the ticket is reopened.
- **Optimistic locking**: `@Version Long version` on the entity. `TicketRequest.version` is optional; if sent
  on update and stale the service throws `ObjectOptimisticLockingFailureException`. Genuine concurrent
  writes are caught by Hibernate. Both map to **HTTP 409** in `GlobalExceptionHandler`.
  Omitting `version` keeps the old behaviour, so existing clients still work.
- **DTOs**: new `TicketResponse` (id, title, description, status, priority, createdAt, updatedAt,
  resolvedAt, version). The service returns DTOs only; the entity is no longer reachable from the controller.
  Service uses `saveAndFlush` so the returned version/updatedAt are current.
- **Validation**: status/priority are validated by a small `@ValidEnum` constraint (derived from the enums,
  no duplicated value lists), keeping per-field error messages; `version` must be >= 0.
- **Structure**: `ticket` package (entity, enums, repository, service, controller, DTOs, not-found exception)
  and `error` package (`GlobalExceptionHandler`, `ApiError`). Application class stays in the root package.
- **Frontend (only what was needed)**: `Ticket` model gains `resolvedAt` and `version`; `TicketRequest` gains
  optional `version`; the edit form sends the loaded version; the detail page shows a "Resolved" row when set.

### Files changed
Backend main: `ticket/Ticket.java`, `ticket/TicketRepository.java`, `ticket/TicketService.java`,
`ticket/TicketController.java`, `ticket/TicketRequest.java`, `ticket/TicketNotFoundException.java` (all moved
from root package; edited as listed), `error/GlobalExceptionHandler.java`, `error/ApiError.java` (moved;
handler gained 409), `src/main/resources/application.properties`.
Backend new: `ticket/TicketStatus.java`, `ticket/TicketPriority.java`, `ticket/TicketResponse.java`,
`ticket/ValidEnum.java`, `ticket/EnumNameValidator.java`, `TimeConfig.java`.
Backend tests (moved to `ticket` package and updated/extended): `TicketServiceTest`, `TicketControllerTest`,
`TicketRepositoryTest`, `TicketApiIntegrationTest`; `src/test/resources/application.properties`.
Frontend: `ticket.model.ts`, `ticket-form.ts`, `ticket-detail.html`, specs `ticket-form`, `ticket-detail`,
`ticket-list` (fixtures + new tests).
Docs: `CHANGES.md` (one superseded decision line), `docs/AI_PROGRESS.md`.

### Tests executed and results
- **Backend: NOT run.** Maven Central is blocked in this environment (HTTP 403 from the sandbox proxy,
  so Spring Boot 4.1.1 and dependencies cannot be downloaded) and the available JDK is 21 while the
  project requires 25. No backend compile, test or build result exists.
- Backend static checks performed: `javac` over all 20 Java files reported no syntax errors (the only errors
  were missing third-party packages); no remaining references to `String` status, `setStatus` or
  `LocalDateTime`; package declarations match directories; test imports checked by hand.
- **Frontend**: `npm ci` OK; `ng test --watch=false`: 6 files, 36 tests passed (33 before, 3 added);
  `ng build` succeeded.

### Known issues
- Backend changes are unverified by a compiler/test run. First thing to do on a machine with JDK 25 and
  network: `cd backend && ./mvnw test`. Likeliest points of friction: Spring Boot 4 / Jackson 3 behaviour in
  the new JSON assertions (ISO instant format, `length()` of 9 fields) and H2 handling of the new columns.
- Existing MySQL data: with `ddl-auto=update`, `version` (NOT NULL) and `resolvedAt` columns are added;
  existing rows get version 0 and no `resolvedAt` (already-resolved tickets have no resolution time).
  Existing `createdAt/updatedAt` values were written as server-local time; reading them as UTC will shift
  them by the server's UTC offset (SAST = 2h) unless they are converted or the server already ran in UTC.
- Optimistic-lock protection from the UI only applies to edits (version is sent); a client that omits
  `version` can still overwrite a concurrent change (DB-level locking still protects true races).
- The frontend list/detail test fixtures use timestamps without `Z`; harmless for display.

### Next command in the sequence
Command 3 (completed, below).

## Command 3 – Ticket workflow and business rules: COMPLETE (backend tests written but not run, see below)

### Implemented workflow and business rules
- **Workflow** (single source of truth: `TicketStatus.allowedTransitions()`):
  OPEN -> IN_PROGRESS, CANCELLED; IN_PROGRESS -> OPEN, RESOLVED, CANCELLED; RESOLVED -> OPEN (reopen), CLOSED;
  CLOSED and CANCELLED are final. OPEN -> RESOLVED/CLOSED is rejected (work must go through IN_PROGRESS).
  Decisions: added `CANCELLED` (cancellation is supported); kept the existing `CLOSED` as the archival step after
  RESOLVED; allowed IN_PROGRESS -> OPEN so a mistakenly started ticket can go back to the queue.
- **Enforcement point**: `Ticket.changeStatus()` is the only way to change status and throws
  `TicketWorkflowException` on a forbidden move (re-sending the current status is a no-op), so create, update
  and any future code path cannot bypass the rules. The service additionally rejects edits to final tickets.
- **Errors**: `TicketWorkflowException` -> **409** in the existing `GlobalExceptionHandler`/`ApiError` format with a
  message such as `Cannot change ticket status from OPEN to RESOLVED. Allowed: [IN_PROGRESS, CANCELLED].`
  Same 409 for: creating a ticket with a status other than OPEN, and editing a CLOSED/CANCELLED ticket.
  A forbidden transition is detected before any field is modified, and the transaction rolls back, so nothing
  is partially saved.
- **Resolution**: `resolvedAt` is set on the move into RESOLVED, kept on RESOLVED -> CLOSED, cleared on reopen,
  never set by CANCELLED. Invariant: `resolvedAt != null` iff status is RESOLVED or CLOSED (for tickets created
  through the API).
- **Creation/update rules**: new tickets always start OPEN (an explicit OPEN is accepted); title/description
  required and non-blank, max 150/2000; priority required; status/priority must be valid enum names; unknown
  ticket -> 404; stale `version` -> 409.
- **API**: `TicketResponse` now includes `allowedTransitions` (statuses reachable from the current one), so
  clients need not duplicate the rules. `README.md` documents the workflow.
- **Frontend (compatibility)**: `CANCELLED` label/style added; model gains `allowedTransitions`; `status` is
  optional in `TicketRequest`. The create form no longer shows or sends a status; the edit form offers only the
  current status plus the allowed transitions; backend 409 messages show in the existing error notification.

### Files changed
Backend main: `ticket/TicketStatus.java` (workflow), `ticket/Ticket.java` (guarded `changeStatus`),
`ticket/TicketService.java`, `ticket/TicketRequest.java`, `ticket/TicketResponse.java`,
`error/GlobalExceptionHandler.java`; new `ticket/TicketWorkflowException.java`.
Backend tests: new `TicketStatusTest`, `TicketTest`; updated `TicketServiceTest`, `TicketControllerTest`,
`TicketRepositoryTest`, `TicketApiIntegrationTest`.
Frontend: `ticket.model.ts`, `ticket-form.ts`, `ticket-form.html`, `styles.css`, specs `ticket-form`,
`ticket-detail`, `ticket-list`.
Docs: `README.md`, `docs/AI_PROGRESS.md`.

### Tests executed and results
- **Passed (executed): frontend** `ng test`: 6 files, 39 tests passed (36 before, 3 net new); `ng build` succeeded.
- **Passed (executed, scratch harness, not part of the repo):** the real `Ticket`, `TicketStatus` and
  `TicketWorkflowException` classes compiled against stub annotations and run directly: all 25 from/to status
  pairs behave exactly as the workflow table says, state is unchanged after a rejection, and the
  resolvedAt invariant holds after every pair; resolve/close/reopen timestamps and both error messages are as
  asserted in the unit tests. This covers the entity logic only.
- **Could not run: all Maven backend tests** (`TicketStatusTest`, `TicketTest`, `TicketServiceTest`,
  `TicketControllerTest`, `TicketRepositoryTest`, `TicketApiIntegrationTest`, context test). Maven Central is
  blocked in this environment and the JDK is 21 while the project needs 25; downloads were not retried.
  `javac` over all Java files reported no syntax errors (only missing third-party packages).
- Failed: none observed.

### Known issues
- Backend Spring/JPA/MockMvc tests are unverified; run `cd backend && ./mvnw test` on JDK 25 with network access.
- Rows created before Command 2 that are RESOLVED/CLOSED have no `resolvedAt`; it is not backfilled
  (re-sending the same status does not invent one). Resolving such a ticket's successor states works normally.
- Existing MySQL data: legacy status values are unchanged; `CANCELLED` needs no migration when the column is
  `varchar` (created by Hibernate under `ddl-auto=update`). A column created by Hibernate 7 as a MySQL native
  `enum(...)` would not include `CANCELLED` until altered.
- Final (CLOSED/CANCELLED) tickets are read-only by design; the edit form still opens for them and shows the
  409 message on save.
- Delete is allowed in any status (unchanged behaviour).

### Next command in the sequence
Command 4 (completed, below).

## Command 4 – Pagination, filtering, search and sorting: IMPLEMENTED (nothing could be built or run, see below)

### Pagination implementation
- `GET /api/tickets` now returns `PageResponse<TicketResponse>`: `content`, `page` (zero-based), `size`,
  `totalElements`, `totalPages`, `hasNext`, `hasPrevious`. Built from Spring Data's `Page`, which is not exposed.
- Defaults: page 0, size 20. Size above 100 is **reduced to 100** (the response reports the size used);
  `page < 0`, `size < 1` or non-numbers are a 400. An empty database or a page past the end returns
  `content: []` with correct totals (`totalPages` 0 when nothing matches).
- The repository now also extends `JpaSpecificationExecutor`; `TicketService.listTickets` calls
  `findAll(Specification, Pageable)`, so SQL `LIMIT/OFFSET` and a `COUNT` query are used. `getAllTickets()` was removed.
- **Backward compatibility:** the response shape of `GET /api/tickets` is a deliberate breaking change
  (bare array -> page object). A tolerant dual-shape response was rejected as inconsistent. Old calls without
  parameters still return 200 but only the first 20 tickets, under `content`. Documented in the README.

### Sorting implementation
- `sortBy`: `id`, `createdAt`, `updatedAt`, `priority`, `status` (case-insensitive; enum `TicketSortField`);
  `direction`: `asc`/`desc`. Default `createdAt desc`. Anything else is a 400 listing the allowed values, so no
  arbitrary entity property can be sorted on. Sorting is part of the SQL `ORDER BY`.
- `id` is always appended as a tie-breaker so pages are stable.
- Priority/status are stored as strings, which would sort alphabetically. `Ticket` has two read-only
  `@Formula` columns (`priorityRank`, `statusRank`, SQL `CASE`) used only via `TicketSortField`, giving
  LOW < MEDIUM < HIGH and OPEN < IN_PROGRESS < RESOLVED < CLOSED < CANCELLED. Not part of the DTO or the schema.
  If an enum is added or reordered, the `CASE` formulas in `Ticket` must be updated.

### Filtering implementation
- `status` and `priority`: one or more values (repeated or comma-separated, case-insensitive); values within one
  filter are OR, different filters AND. `createdFrom` / `createdTo`: ISO dates, UTC, both inclusive
  (`createdAt >= start of from` and `< start of day after to`); `createdTo < createdFrom` is a 400.
- All filters are optional and built into a `Specification` (`TicketSpecifications`), i.e. SQL `WHERE`.
- Invalid values give 400, `message: "Invalid query parameters"`, and one entry per bad parameter in `fieldErrors`
  (all problems reported together; new `InvalidTicketQueryException` handled in `GlobalExceptionHandler`).
- Unknown extra query parameters are ignored. No other existing field was added as a filter (title/description
  are covered by search; `resolvedAt` is implied by status).

### Search implementation
- `q` (max 100 chars, trimmed, blank ignored): `lower(title) LIKE`, `lower(description) LIKE` (substring), or
  `id = n` when the text is a number (`42` or `#42`). `%`, `_` and `\` in the text are escaped so they match literally.
  Combines with filters, sorting and paging in the same SQL. Note a leading-wildcard `LIKE` cannot use an index,
  so on very large tables a full-text index would be the next step.

### Frontend changes (only what the API change required, plus minimal paging UI)
- `ticket.model.ts`: `Page<T>`, `TicketListParams`, sort-field list. `ticket-api.ts`: `list(params)` returns a
  page and builds query params (omits empty ones; repeats array values).
- `ticket-list.ts/.html`: filtering, search and sorting are now done by the server instead of in the browser.
  Kept: status filter, search box, delete confirmation, error/retry, empty states. Added: priority filter, sort
  dropdown, Previous/Next pager with "Showing x-y of n". Search is sent when the search form is submitted
  (no per-keystroke requests). Changing a filter/sort returns to page 1; a stale response is cancelled; deleting
  reloads the page and steps back if the page no longer exists. `styles.css`: toolbar/pager tweaks.
- Detail, form and CRUD code were not touched.

### Files changed
Backend main: new `TicketListQuery`, `TicketSortField`, `TicketSpecifications`, `PageResponse`,
`InvalidTicketQueryException`; edited `Ticket` (rank formulas; `@PrePersist` now only fills timestamps that are
still null, so tests/imports can persist given times - the API still cannot set them), `TicketRepository`,
`TicketService`, `TicketController`, `error/GlobalExceptionHandler`.
Backend tests: new `TicketListQueryTest` (20), `TicketListApiIntegrationTest` (36, real H2 SQL); updated
`TicketControllerTest` (24 total), `TicketServiceTest` (19 total), `TicketApiIntegrationTest` (list assertions).
Frontend: `ticket.model.ts`, `ticket-api.ts`, `ticket-list.ts`, `ticket-list.html`, `styles.css`, specs
`ticket-list.spec.ts` (rewritten, 18 tests), `ticket-api.spec.ts` (11 tests).
Docs: `README.md` (parameters, response, breaking-change note), `docs/AI_PROGRESS.md`.

### Tests executed and results
- **Executed, passed (scratch harness, not part of the repo):** the real `TicketListQuery`, `TicketSortField`,
  `InvalidTicketQueryException`, `TicketStatus`, `TicketPriority` compiled against small stubs of Spring's
  `Sort`/`PageRequest` and run directly: 34 checks, 0 failed (defaults, size clamp, invalid page/size/sort/
  direction/status/priority/dates/q, multi-value and case handling, date bounds, sort + id tie-breaker, all
  errors reported together). This covers query parsing only, not SQL.
- **Static checks:** all backend Java files were run through `javac` (JDK 21 compiler module). 1129 errors, every
  one a missing third-party package/symbol (Spring, Hibernate, JUnit...) or the `@Override`s of
  `EnumNameValidator`, whose jakarta supertype is missing; no syntax errors. `tsc` over the changed frontend files:
  only unresolved Angular/vitest module and test-global errors, no syntax errors. Templates are not checked.
- **Could not run (blocked): all Maven backend tests** (including the 56 new/updated ones listed above) -
  no Maven, JDK 21 only (project needs 25), network disabled, no Spring jars. **Frontend tests and `ng build`:**
  not run, no `node_modules` and no network. Not retried.
- Failed: none observed. Passed: only the scratch harness above.

### Known issues
- Everything database-related is unverified, in particular: the `@Formula` rank columns in `ORDER BY` with
  Hibernate 7 on H2 and MySQL; `LIKE ... ESCAPE '\\'` on both; `Instant` comparisons in the date filters;
  the MockMvc/JSONPath assertions. First step next time: `cd backend && ./mvnw test` on JDK 25, then
  `cd frontend && npm ci && ng test --watch=false && ng build`.
- If the `@Formula` approach misbehaves, the fallback is a `CASE` ordering in a custom query instead.
- Breaking API response shape (see above): any client other than this frontend must read `content`.
- Dates in `createdFrom`/`createdTo` are UTC days; a user in SAST (UTC+2) sees a 2-hour offset at day boundaries.
- Search is a substring `LIKE` (no relevance ranking, no index use).
- Frontend: page size is fixed (server default 20) and there is no date-range UI, although the API supports both.

### Next command in the sequence
Command 5 (completed, below).

## Command 5 – Database migrations and API documentation: IMPLEMENTED (nothing could be built or run, see below)

### Database migration system
- **Flyway** added (`spring-boot-starter-flyway` + `flyway-mysql`, versions managed by the Boot BOM). Nothing existed before.
  Migrations are in `backend/src/main/resources/db/migration`; Flyway runs them at startup in every profile.
- **Migrations created (3):**
  - `V1__create_tickets_table.sql` - the `tickets` table exactly as the `Ticket` entity defines it today
    (this already contains everything Commands 2-4 added: `resolved_at`, `version`, `CANCELLED` as a valid value).
    `status`/`priority` are `VARCHAR(20)`, timestamps `DATETIME(6)`, `version BIGINT NOT NULL DEFAULT 0`.
  - `V2__add_ticket_check_constraints.sql` - `CHECK` on status, priority and `version >= 0`.
  - `V3__add_ticket_list_indexes.sql` - `created_at`, `(status, created_at)`, `(priority, created_at)`, `updated_at`.
- **Why no separate migrations for Commands 2-4:** a fresh database gets the final schema from V1; replaying the
  history would only add files that can fail on databases already changed by `ddl-auto=update`. V2 (constraints) and
  V3 (indexes) are separate because MySQL DDL is not transactional: a data-dependent constraint failure must not leave a
  half-applied index file. No repeatable migrations: there are no views/routines. All migrations are additive.
- **Existing databases:** Flyway refuses a non-empty unversioned schema by default (protects data). One-off adoption with
  `FLYWAY_BASELINE_ON_MIGRATE=true` (`baseline-version=1`: V1 recorded as applied, V2/V3 run). README documents a
  non-destructive SQL step to bring a Hibernate-created table to the V1 shape first (enum -> `VARCHAR`, add
  `resolved_at`/`version`). That SQL was not executed against a real MySQL.
- **Reliability review:** no foreign keys (single table), no unique keys beyond the primary key (none justified),
  constraints as above, indexes only where the list query can use them. Deliberately not indexed: priority/status *sort*
  (ordered by a `CASE` rank) and title/description search (leading-wildcard `LIKE`).

### Hibernate configuration
- `spring.jpa.hibernate.ddl-auto=validate` in main and test config; `update` and `create-drop` are gone from
  configuration (the one exception is the repository slice test, below). Hibernate cannot change the schema in any profile.
- `Ticket` mapping made portable: `@JdbcTypeCode(SqlTypes.VARCHAR)` on status/priority (otherwise Hibernate 7 maps
  string enums to MySQL native `ENUM`, which cannot hold `CANCELLED` and would fail validation against `VARCHAR`),
  `@JdbcTypeCode(SqlTypes.TIMESTAMP)` on the three `Instant` columns (otherwise H2 expects
  `TIMESTAMP WITH TIME ZONE`, which MySQL lacks), `version` is `nullable = false`. Values are still stored as UTC via
  `hibernate.jdbc.time_zone=UTC`; the connector time zone was intentionally not changed (see Known issues).
- Tests run the **same migration files** on H2 (`MODE=MySQL`) with `validate`, so MySQL and H2 use one schema definition.

### API documentation
- **springdoc-openapi 3.0.0** (`springdoc-openapi-starter-webmvc-ui`; the 3.x line is the one for Spring Boot 4), pinned in
  `pom.xml` as `springdoc.version`.
- `OpenApiConfig` (title, description, error format, timestamps, concurrency; states that no authentication exists, so no
  security scheme is advertised). `TicketController` documents every operation: summary, description, all parameters
  (page/size/sortBy/direction/status/priority/createdFrom/createdTo/q with defaults, limits and allowed values), and
  response codes 200/201/204/400/404/409/500 with the `ApiError` schema and examples (validation 400, workflow 409,
  list 400). DTO records (`TicketRequest`, `TicketResponse`, `PageResponse`, `ApiError`) carry field descriptions/examples;
  required fields and size limits come from the validation annotations.
- **Availability:** off by default (`springdoc.*.enabled=false` in `application.properties`); the new
  `application-dev.properties` turns it on. Run with `SPRING_PROFILES_ACTIVE=dev`: Swagger UI
  `http://localhost:8080/swagger-ui.html`, document `/v3/api-docs`.

### Configuration cleanup
- `application.properties` rewritten (no duplication; Flyway, validate, UTC, docs off); new `application-dev.properties`;
  test `application.properties` aligned. No `application.yml` exists and none was added. DB credentials were already
  `${DB_USERNAME}`/`${DB_PASSWORD}` with no defaults; no secrets in the repository. `.env.example` gained
  `SPRING_PROFILES_ACTIVE` and `FLYWAY_BASELINE_ON_MIGRATE`. The migration runs as the application's DB user (needs DDL rights).

### Files changed
Backend main: `pom.xml`, `resources/application.properties`, `ticket/Ticket.java`, `ticket/TicketController.java`,
`ticket/TicketRequest.java`, `ticket/TicketResponse.java`, `ticket/PageResponse.java`, `error/ApiError.java`.
Backend new: `OpenApiConfig.java`, `resources/application-dev.properties`, `resources/db/migration/V1__..., V2__..., V3__...`.
Backend tests: new `DatabaseMigrationTest` (5), `OpenApiDocumentationTest` (6), `MainConfigurationTest` (3);
`TicketRepositoryTest` (slice test now sets `spring.flyway.enabled=false` and `ddl-auto=create-drop` on itself so it does not depend
on whether the Boot 4 JPA test slice auto-configures Flyway; the migrations are covered by `DatabaseMigrationTest`);
`resources/application.properties` (test).
Other: `README.md` (setup, migrations, adoption steps, Swagger URLs, tests, production notes), `.env.example`, `CHANGES.md`,
`docs/AI_PROGRESS.md`. Frontend: no change.

### Tests executed and results
- **Could not run: any Maven build or test** (Maven Central unreachable - connection fails; JDK 21 installed, project needs
  25). No dependency was downloaded and nothing was retried. Existing backend tests were therefore not re-run.
- **Executed (static):**
  - `javac` over all backend main and test sources: no syntax errors; every error is a missing third-party package/symbol.
  - Migration SQL run on SQLite after removing `AUTO_INCREMENT`: V1 and V3 execute; the three V2 `CHECK` expressions reject
    `BOGUS` status, `URGENT` priority and `version = -1` and accept `CANCELLED`/`HIGH`/`0`; all 4 indexes created. This is a
    syntax/logic check only - **not** MySQL or H2.
  - Documentation searched: Spring Boot 4 requires `spring-boot-starter-flyway` plus `flyway-mysql` for MySQL; springdoc 3.x is
    the Boot 4 line.
- Failed: none observed. Passed: only the static checks above.

### Known issues
- **Unverified at runtime.** First step next time on JDK 25 with network: `cd backend && ./mvnw test`. Likeliest friction:
  (1) H2 acceptance of `DATETIME(6)`/`AUTO_INCREMENT`/`CHECK ... IN` in MySQL mode and Hibernate `validate` of the
  `TIMESTAMP`/`VARCHAR` columns; (2) springdoc 3.0.0 with Boot 4.1.1 (bump `springdoc.version` if needed), schema naming of
  `PageResponse<TicketResponse>`, and the JSONPath assertions in `OpenApiDocumentationTest`; (3) Flyway/`Flyway` bean and
  `MigrationInfo` API names in `DatabaseMigrationTest`.
- The adoption SQL in the README and the migrations were never run on a real MySQL 8. Try them on a copy of the data first.
  `CHECK` constraints need MySQL 8.0.16 or later (older versions silently ignore them).
- Timestamps: the MySQL connector time zone was left unchanged (changing it would shift existing rows). Rows written by older
  versions in server-local time read as UTC; run the server in UTC. The rank `@Formula` columns for sorting are unchanged and still unverified.
- Enum change needs a migration (constraint) in addition to the code; `DatabaseMigrationTest` catches a forgotten one.
- No authentication exists, so none is documented; add security schemes to `OpenApiConfig` when it is introduced.

### Next command in the sequence
Command 6A (completed, below). Original note for Command 6: first step regardless: run `cd backend && ./mvnw test` on JDK 25 with network access,
then `cd frontend && npm ci && ng test --watch=false && ng build`, and fix anything they surface.


## Command 6A – Backend authentication, user model and security: IMPLEMENTED (nothing could be built or run, see below)

### Authentication architecture chosen
**Server-side session + HttpOnly cookie**, with CSRF protection. Reasons: the Angular app and API are same-origin in development
(`/api` proxy) and deployment, so cookies need no token storage in JavaScript (immune to token theft via XSS), logout truly
invalidates the session, and Spring Security supports it natively with the least code. JWT was rejected (revocation, storage
problem, more moving parts). Only this one mechanism exists; form login and HTTP Basic are explicitly disabled.

### User model
- `app_users` table (migration `V4__create_app_users_table.sql`, additive, no data touched): `id` (PK, auto increment),
  `username` VARCHAR(50) UNIQUE, `email` VARCHAR(254) UNIQUE, `password_hash` VARCHAR(100), `enabled` BOOLEAN default true,
  `created_at`, `updated_at` DATETIME(6), plus CHECKs on minimum lengths. The two UNIQUE constraints are the lookup indexes.
- `user` package: `AppUser` (entity, `toString` omits the hash), `AppUserRepository`, `AppUserPrincipal` (UserDetails; hash erased
  after authentication, no authorities yet), `AppUserDetailsService` (by username, or by email if the text contains `@`),
  `UserService` (the only place passwords are hashed; normalises username/email to trimmed lower case; validates), `PasswordPolicy`,
  `UserResponse` (id, username, email: the only account DTO), `InvalidUserException` (400), `DuplicateUserException` (409),
  `BootstrapUserRunner` (opt-in first account from environment variables, only while the table is empty, not privileged, no
  built-in credentials). No registration endpoint and no user administration (later command).
- Usernames: 3-50 chars `[a-z0-9._-]`, no `@`, so an identifier with `@` is unambiguously an email.

### Security configuration (`auth` package)
- `SecurityConfig`: BCrypt encoder (strength property, 12), `DaoAuthenticationProvider` (checks `enabled`), explicit
  `SecurityContextRepository`, request cache disabled (anonymous callers never get a session), CORS bean (exact origins from
  `app.cors.allowed-origins`, credentials allowed, never `*`), logout at `POST /api/auth/logout` (invalidates session, deletes
  `JSESSIONID`, 204).
- Public: `POST /api/auth/login`, `GET /api/auth/csrf`, `/error`, and the springdoc paths (which only exist under the `dev`
  profile). Everything else, including unknown paths, `anyRequest().authenticated()`. No role checks (Command 7).
- CSRF stays **on** for all state-changing requests including login and logout: `CookieCsrfTokenRepository.withHttpOnlyFalse()`
  (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`) with the Spring SPA handler and `CsrfCookieFilter`.
- Cookies: HttpOnly, SameSite=Lax, `Secure` default true (`SESSION_COOKIE_SECURE`; `dev` profile false), 30 min timeout.
- `SecurityErrorHandlers` writes 401/403 from the filter chain in the existing `ApiError` shape with fixed messages; no
  `WWW-Authenticate` header. `GlobalExceptionHandler` gained 401/400/409 handlers for the new exceptions.
- `AuthController`: login (one generic 401 for unknown user, wrong password and disabled account; failure log line has no username
  or password; session id changed on login against fixation; `LoginRequest.toString` masks the password), `/me` (re-reads the
  account; ends the session if it was removed or disabled), `/csrf`. OpenAPI now advertises a cookie security scheme.

### Files changed
New main: `auth/{AuthController, AuthenticationFailedException, CsrfCookieFilter, LoginRequest, SecurityConfig, SecurityErrorHandlers,
SpaCsrfTokenRequestHandler}.java`, `user/{AppUser, AppUserRepository, AppUserPrincipal, AppUserDetailsService, UserService,
PasswordPolicy, UserResponse, InvalidUserException, DuplicateUserException, BootstrapUserRunner}.java`,
`db/migration/V4__create_app_users_table.sql`.
Edited main: `pom.xml` (`spring-boot-starter-security`, test `spring-boot-starter-security-test`), `error/GlobalExceptionHandler.java`,
`OpenApiConfig.java`, `application.properties`, `application-dev.properties`.
New tests: `auth/AuthApiIntegrationTest` (15), `auth/SecurityConfigurationTest` (14), `user/UserServiceTest` (6), `user/PasswordPolicyTest` (4).
Edited tests: `TicketApiIntegrationTest`, `TicketListApiIntegrationTest`, `TicketControllerTest` (run as `@WithMockUser`, mutating calls send
`csrf()`: security is on, not bypassed), `DatabaseMigrationTest` (V4 + unique keys), `OpenApiDocumentationTest` (auth documented instead of
"none"), `MainConfigurationTest` (cookie/CORS/bootstrap defaults), test `application.properties` (BCrypt strength 4, non-Secure cookies, CORS origin).
Other: `README.md`, `.env.example`, `docs/AI_PROGRESS.md`. Frontend: not touched.

### Tests executed and results
- **Blocked: all Maven builds and tests.** No Maven, only JDK 21 compiler module (project needs 25), Maven Central unreachable;
  not retried. **None of the new or edited backend tests has been run.**
- **Static check performed:** `javac` (via the `jdk.compiler` module) over all main and test sources: 2125 errors, all missing
  third-party packages/symbols (Spring, Hibernate, JUnit...) and the `@Override`s of methods whose supertypes are missing from the
  classpath. No other kind of error was reported for the new `auth`/`user` files, so no syntax errors were found; this does not
  prove the code compiles against Spring Security 7.
- Failed: none observed. Passed: none executed.

### Known issues
- **Unverified at runtime.** First step: `cd backend && ./mvnw test` on JDK 25 with network. Likeliest friction: Spring Security 7 / Boot 4.1
  API names (`DaoAuthenticationProvider(UserDetailsService)`, `CookieCsrfTokenRepository.setCookieCustomizer`, the
  `spring-boot-starter-security-test` artifact id), `@WebMvcTest` picking up default security in `TicketControllerTest`,
  H2 acceptance of `CHAR_LENGTH` CHECKs and Hibernate validation of the BOOLEAN column on MySQL, and `MockHttpSession`
  behaviour after logout in the logout tests.
- A session of an account disabled/deleted after login keeps working on ticket endpoints until `/api/auth/me` is called or the
  session times out (the principal is not re-checked on every request). Candidate for Command 6B/7.
- No brute-force/lockout or rate limiting on login. No registration/password change/reset endpoint; the first account comes from the
  optional bootstrap variables.
- The Angular app has no login yet, so its ticket pages get 401 until Command 6B. After logout the CSRF cookie is cleared; the client
  must call `GET /api/auth/csrf` before the next login.
- Swagger UI/`/v3/api-docs` are public when the `dev` profile enables them.
- Existing MySQL databases adopted via Flyway baseline get V4 applied automatically on next start (new table only).

### Next command in the sequence
Command 6B (Angular login, logout and session handling against these endpoints). First step regardless: run the backend tests above.


## Command 6B – Angular login, authentication state and route protection: IMPLEMENTED (frontend verified; NOT verified against a running backend)

### Backend integration details (read from the real 6A code, nothing invented)
- Endpoints used: `GET /api/auth/csrf` (204, sets `XSRF-TOKEN`), `POST /api/auth/login` (body `{username, password}`; the
  username field accepts username or email; 200 -> `{id, username, email}`; generic 401 `Invalid username or password`),
  `GET /api/auth/me` (200 user / 401), `POST /api/auth/logout` (204). Errors use the `ApiError` shape (`message`, `fieldErrors`).
- Session: HttpOnly `JSESSIONID` cookie, handled entirely by the browser. Same-origin through the `/api` dev proxy, so no
  `withCredentials` or CORS change was needed (that would only matter cross-origin). Nothing is stored in
  localStorage/sessionStorage (a test asserts this).
- CSRF: cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN` = Angular's default, so `provideHttpClient` adds it to POST/PUT/DELETE
  with no custom code. Because logout deletes the cookie, `login()` always calls `GET /api/auth/csrf` first. The exact text
  `Invalid or missing CSRF token` (from `SecurityErrorHandlers`) triggers one retry after re-fetching the token.

### Frontend authentication implementation
- `auth/auth.service.ts` (the only auth service): signals `user`, `isAuthenticated`, `initialized`; `login`, `logout`,
  `loadCurrentUser`, `ensureLoaded`, `csrf`, `sessionExpired`. Logout clears local state and goes to `/login` even if the
  server was unreachable (and then warns the user); `sessionExpired` acts only while someone is signed in, so a burst of 401s
  gives one notice and one redirect.
- `auth/login.ts|html`: reactive form, required + whitespace-only + max-length validation (254 / 128, as the backend),
  show/hide password (`aria-pressed`), loading state (button disabled, `aria-busy`, double-submit ignored), `role="alert"`
  error from the backend message (or "Cannot reach the server"), password cleared and focused after a failure, proper
  `autocomplete`, labels, responsive CSS in `styles.css`.
- `auth/auth.guard.ts`: `authGuard` (redirects to `/login?returnUrl=<url>`), `guestGuard` (signed-in users leave `/login`).
  `auth/return-url.ts`: `returnUrl` must be an in-app path (no `//`, backslash, absolute URL, or `/login`), else `/tickets`.
- `auth/auth.interceptor.ts`: only `/api/` requests. 401 outside `/api/auth/*` -> `sessionExpired()`; `/api/auth/*` 401s are left
  to the caller so login errors and the startup `/me` check are not turned into redirects. CSRF 403 -> fetch token, repeat once.
- `app.config.ts`: `withInterceptors([authInterceptor])` and `provideAppInitializer` loading `/me` before the first render
  (no header/route flicker; an unreachable server just means "signed out"). `app.routes.ts`: `/login` + everything else under
  `authGuard` (including the not-found route). `app.html/ts`: nav, "Signed in as <username>" and "Sign out" only when signed in.
- No role-based checks (Command 7). No ticket code changed.

### Files changed
New: `frontend/src/app/auth/{auth.model, auth.service, auth.guard, auth.interceptor, return-url, login}.ts`, `login.html`,
specs `auth.service.spec, auth.guard.spec, auth.interceptor.spec, login.spec, return-url.spec`.
Edited: `app.config.ts`, `app.routes.ts`, `app.ts`, `app.html`, `app.spec.ts`, `styles.css`, `README.md`, `CHANGES.md`, `docs/AI_PROGRESS.md`.
Backend: not changed.

### Tests executed and actual results
- `npm ci`: worked (registry reachable). `npx ng build`: succeeded. `tsc --noEmit` on `tsconfig.app.json` and
  `tsconfig.spec.json`: no errors.
- `npx ng test --watch=false` (Vitest): **101 passed, 1 failed (102)**. All new/updated auth specs pass (service, guards, interceptor,
  login form, app shell, return URL). Coverage: validation, successful and failed login, loading state, auth state, current-user
  retrieval, logout, guard behaviour, session expiry, interceptor behaviour. HTTP is mocked only in tests.
- The 1 failure is **pre-existing and unrelated**: `ticket-list.spec.ts > steps back a page when deleting the last ticket on the
  final page` (expects `page=1`, receives `0`). It fails identically on the unmodified 6A project (49 passed / 1 failed there);
  not caused by this command and left untouched.
- Backend authentication was **not run** (no JDK 25 / Maven in this environment, as in 6A), so login, cookies and CSRF
  were **not verified end to end**; compatibility was checked by reading `AuthController`, `SecurityConfig`,
  `SecurityErrorHandlers`, `CsrfCookieFilter` and `LoginRequest`.

### Known issues
- End-to-end check still needed: start the backend (`dev` profile, bootstrap user variables), `ng serve`, sign in, refresh,
  create a ticket, sign out. Likeliest friction: the `XSRF-TOKEN` cookie being `Secure` over plain http (use the `dev` profile).
- The pre-existing `ticket-list.spec.ts` failure above.
- Backend limits from 6A remain: no brute-force protection, no registration/password change, a disabled account's session lasts
  until `/me` or timeout (the SPA calls `/me` only on page load).
- No idle-timeout warning; expiry is detected on the next API call or reload.

### Next command in the sequence
Command 7 — Role-Based Access Control & Security Tests.


## Command 7A – Role model and backend permission foundation: IMPLEMENTED (nothing could be built or run, see below)

### Role model
- `user/Role` enum is the single definition: `ADMIN`, `SUPPORT_AGENT`, `USER`. One role per account (`AppUser.role`, `@Enumerated(STRING)`,
  `VARCHAR(20) NOT NULL`, default `Role.USER`, null rejected). Arbitrary values are impossible: enum on the Java side, CHECK constraint
  `ck_app_users_role` in the database, JSON parsing of the admin request (unknown/lower-case names -> 400).
- `UserResponse` is now `{id, username, email, role}` (role is safe to expose; still no password/hash). Frontend untouched; the extra
  JSON field is compatible with the Angular model.
- Role assignment: `UserService.createUser(u, e, p)` always makes a `USER`; `createUser(u, e, p, Role)` is internal (bootstrap, tests) and
  no controller passes a client value to it. `changeRole(actorId, targetId, role)` is reachable only via the admin endpoint; an admin
  cannot change their own role (409), which also means the last admin cannot demote themselves. No registration endpoint, no
  hardcoded account. `BootstrapUserRunner` (still opt-in, still only on an empty table) takes `app.bootstrap-user.role`
  (`BOOTSTRAP_USER_ROLE`, default ADMIN, invalid value stops startup).

### Authority mapping
- `Role.authority()` = `ROLE_` + name. `AppUserPrincipal` carries the role and returns exactly that one `GrantedAuthority`
  (loaded by `AppUserDetailsService` from the persisted row, so login by username or email both get it). No role hierarchy.
- `SessionRoleRefreshFilter` (after `SecurityContextHolderFilter`): for sessions holding an `AppUserPrincipal` it re-reads the account per
  request: deleted/disabled -> session ended (401); role changed -> authentication replaced and saved to the session. This also closes
  the 6A known issue "disabled account's session keeps working". Cost: one primary-key query per authenticated request.

### Endpoint security
- `@EnableMethodSecurity` on `SecurityConfig`; reusable `@AdminOnly`, `@StaffOnly`, `@AnyRole` (+ `auth/Roles` SpEL constants).
- URL rules: public list unchanged from 6A; `/api/admin/**` -> `hasRole('ADMIN')`; everything else still `authenticated()`.
- New `GET /api/admin/users`, `PUT /api/admin/users/{id}/role` (ADMIN only, URL rule + `@AdminOnly`).
- Tickets: class-level `@AnyRole` (any valid role); `DELETE /api/tickets/{id}` is `@AdminOnly` (my reading of "clear" permissions: a
  destructive, privileged operation; 7B may relax it for owners). Ownership/visibility NOT implemented.
- 401/403 shape unchanged (`SecurityErrorHandlers`); `GlobalExceptionHandler` got an `AccessDeniedException` handler because method security
  throws inside the controller layer where the catch-all would give 500 (403 when signed in, 401 otherwise), plus 404/409 handlers
  for the new exceptions. CSRF, CORS, session and cookie configuration untouched (admin PUT needs CSRF like every mutating call).

### Database changes
`V5__add_user_roles.sql`: `ALTER TABLE app_users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'` + CHECK `ck_app_users_role`.
Additive, no data rewritten; every existing account becomes `USER`, none becomes ADMIN. Existing installations nominate the first admin with
the one-line SQL in the README (documented there). Not run on MySQL 8; H2 (MODE=MySQL) only in tests, and not even that yet.

### Files changed
New main: `user/{Role, RoleChangeRequest, RoleChangeNotAllowedException, UserNotFoundException, AdminUserController}.java`,
`auth/{Roles, AdminOnly, StaffOnly, AnyRole, SessionRoleRefreshFilter}.java`, `db/migration/V5__add_user_roles.sql`.
Edited main: `user/{AppUser, AppUserPrincipal, UserResponse, UserService, BootstrapUserRunner}.java`, `auth/SecurityConfig.java`,
`error/GlobalExceptionHandler.java`, `ticket/TicketController.java`, `application.properties`.
New tests: `user/{RoleTest (9), BootstrapUserRunnerTest (4)}`, `auth/{MethodSecurityTest (6), RoleAuthorizationIntegrationTest (18),
RoleAssignmentRestrictionsTest (3)}`, `RoleMigrationTest (1)`. Edited tests: `DatabaseMigrationTest` (V5 + role default/CHECK),
`MainConfigurationTest`, `UserServiceTest` (+4), `AuthApiIntegrationTest` (role in response, accounts removed after each test),
`TicketApiIntegrationTest`, `TicketControllerTest` (run as ADMIN because delete is admin-only).
Other: `README.md`, `.env.example`, `docs/AI_PROGRESS.md`.

### Tests executed and results
- **Blocked: all Maven builds and tests.** Only JDK 21 is installed (project needs 25) and the Spring Boot 4.1.1 parent POM and all
  dependencies cannot be downloaded (offline attempt failed once, not retried). **None of the new or edited tests has been run.**
- **Static check:** `javac` over all main and test sources: only missing-dependency errors (packages/symbols of Spring, JUnit, etc.);
  no syntax-class errors. This does not prove the code compiles or passes.
- Passed: none executed. Failed: none observed. Blocked: all 46 new tests and every edited test.
- Frontend not touched, not re-run (101 passed / 1 pre-existing failure as of 6B).

### Known issues / likeliest friction on the first real run
- Spring Security 7 details I could not check: `@EnableMethodSecurity` proxying `TicketController` (CGLIB) together with springdoc; method-level
  `@AdminOnly` overriding class-level `@AnyRole` on `deleteTicket` (documented behaviour); `AuthorizationDeniedException` reaching
  `handleAccessDenied`; the `@Import`ed `@TestConfiguration` probe in `MethodSecurityTest`; `@WebMvcTest` in `TicketControllerTest`.
- Admin list is unpaged. No endpoint to create/disable accounts or reset passwords (still only bootstrap). No audit log of role changes.
- The Angular app still shows the delete button to everyone; non-admins now get 403 (frontend role controls are a later command).
- Role refresh costs one query per request; fine at this scale, cache if it ever matters.
- `docs`/`README` text for the first admin on an existing installation requires one manual SQL statement by design.

### Next command in the sequence
Command 7B (ticket ownership and visibility rules). First step regardless: `cd backend && ./mvnw test` on JDK 25 with network access.




## Commands 7B–18 – overall status (session of 2026-11-22)

**Overall:** feature-complete for the scope of Commands 7B–18. Frontend verified (build, strict type-check, lint,
125 unit tests). **Backend unverified at runtime**: Maven Central is still refused by the sandbox proxy (HTTP 403;
also repo1, Google mirror, Gradle plugin portal, JitPack) and only JDK 21 is installed, so no Spring code has been
compiled against its dependencies and no backend test has run. Not retried after the first probe.

| Command | Scope | Status |
|---|---|---|
| 7B | Ticket ownership/visibility, RBAC completion, security errors | IMPLEMENTED |
| 8 | Users (admin CRUD, deactivation, password reset, profile), teams, admin UI | IMPLEMENTED (UI COMPLETED) |
| 9 | Assignment, assignment history, workload | IMPLEMENTED |
| 10 | Comments, internal notes, activity timeline | IMPLEMENTED |
| 11 | SLA policies, deadlines, pause/reopen rules, monitor | IMPLEMENTED (pure SLA maths executed: PASS) |
| 12 | Persistent notifications | IMPLEMENTED |
| 13 | Secure attachments | IMPLEMENTED (type detection + sanitiser executed: PASS) |
| 14 | Dashboard analytics | IMPLEMENTED (UI COMPLETED) |
| 15 | Angular frontend | COMPLETED (built, type-checked strictly, 125/125 tests) |
| 16 | Tests | PARTIALLY COMPLETED: frontend executed; backend written, not executed; E2E written, not executed |
| 17 | Migrations V6–V12, API docs, Actuator health | IMPLEMENTED |
| 18 | Docker, Compose, nginx, CI, logging/monitoring | IMPLEMENTED (`docker compose config` valid; images not built: no Docker daemon) |
| Security review | see docs/SECURITY.md | COMPLETED as a code review; findings fixed or documented |
| Documentation | README + docs/* | COMPLETED |

### Features implemented (backend)
- **Visibility/permissions (7B):** `TicketAccessPolicy` (single source: SQL predicate + per-ticket checks), `TicketLookup`
  for every sub-resource, `CurrentUser`/`Viewer` (id, role, team ids from the DB per request; fails closed). Invisible
  tickets = 404. Requester rules: cancel active, reopen/close RESOLVED, edit details only while OPEN. Per-caller
  `allowedTransitions` and a `permissions` object in `TicketResponse`. Generic `NotFound/Conflict/Forbidden/BadRequest`
  exceptions; 413 for oversized uploads; 429 for throttled logins; type-mismatch errors no longer echo input.
- **Users/teams (8):** paged admin search, create (role defaults USER), edit (own role/activation protected), password
  reset, deactivation unassigns active tickets (also when the staff role is removed), `/api/profile` (name, email,
  password with current-password check), `/api/users/assignable`. Teams: CRUD-without-delete, member replacement
  (active staff only), case-insensitive unique names, inactive teams refuse tickets.
- **Assignment (9):** `PUT /api/tickets/{id}/assignment` with validation (active staff, active team, membership),
  `ticket_assignments` history, activity entries, notifications; workload per agent/team with fixed number of grouped
  queries and LOW/MEDIUM/HIGH indicator; list views `mine/assigned/team/unassigned` and filters team/agent/requester/category.
- **Comments/notes/activity (10):** internal notes filtered in SQL, edit by author, delete by admin, staff reply = first
  response, requester reply resumes WAITING tickets; activity for every change (no comment text, no secrets).
- **SLA (11):** new status `WAITING` (pauses resolution clock) and priority `CRITICAL`; `sla_policies` (admin-editable);
  deadlines, warning time, pause accounting, reopen restart, priority recalculation; `SlaInfo` computed server-side;
  `@Scheduled` monitor with once-only flags. Rules: docs/SLA.md.
- **Notifications (12):** entity, recipient-scoped repository, list/unread-count/mark-read/mark-all, `NotificationChannel`
  extension point (e-mail later), messages never contain comment text.
- **Attachments (13):** allow-list + magic bytes/UTF-8 check, size/count limits, sanitised names, random storage keys in
  `ATTACHMENTS_DIR`, path confinement, download as attachment + nosniff, file cleanup after commit/rollback.
- **Dashboard (14):** totals, by status/priority/category, unassigned, overdue, urgent, SLA compliance over decided
  targets, average resolution (`resolution_seconds` column), daily trend, workload for staff; all scoped by visibility.
- **17/18:** Actuator health only (no details), `/actuator/**` otherwise ADMIN; `open-in-view=false`; login limiter
  (5 failures/username/15 min); Dockerfiles, nginx with CSP and security headers, compose with MySQL 8.4 and volumes,
  GitHub Actions (backend verify, frontend lint/test/build, image builds, Playwright against compose).

### Database changes
V6 teams, team_members, app_users.full_name · V7 tickets.category/requester_id/assignee_id/team_id + FKs + indexes ·
V8 CHECK constraints for WAITING, CRITICAL, categories (needs MySQL 8.0.19+ for `DROP CONSTRAINT`) · V9 ticket_comments,
ticket_activity, ticket_assignments · V10 sla_policies (seeded) + per-ticket SLA columns + indexes · V11 notifications ·
V12 ticket_attachments. All additive; legacy tickets: category OTHER, no requester (staff-only), no SLA.

### API changes
New endpoints listed in docs/API.md. Breaking: `GET /api/admin/users` returns a page instead of an array;
`TicketResponse` has 16 fields (category, requester, assignee, team, sla, permissions added); `UserResponse` adds
fullName, enabled, createdAt; login returns the stored account (with fullName).

### Frontend changes
New shell (sidebar by role, notification bell polling every 60 s, user chip), dashboard (KPI tiles, bar lists, SVG trend
with hover + table view; palette validated with the dataviz validator), ticket queues and filters, rebuilt ticket detail
(status actions, SLA panel, assignment, conversation with internal notes, attachments, timeline, confirmation dialogs),
category on the form, admin users/teams/SLA pages, profile, notifications page, role guards. `strict` +
`strictTemplates` enabled (no errors). Critical-CSS inlining disabled so the strict CSP works. Sources formatted with the
project's Prettier config; `lint`, `test:ci`, `e2e` scripts. Fixed the pre-existing failing list test (the list now
syncs its page number with the page the server returned).

### Files changed (summary)
Backend new packages: `assignment`, `comment`, `activity`, `sla`, `notification`, `attachment`, `dashboard`, `team`;
new in `auth`: `Viewer`, `CurrentUser`, `LoginAttemptLimiter`, `TooManyAttemptsException`; new in `ticket`:
`TicketAccessPolicy`, `TicketPermissions`, `TicketLookup`, `TicketStatusChanger`, `TicketView`, `TicketCategory`,
`TicketMessages`, `StatusChangeRequest`; new in `user`: admin/profile DTOs, `UserSummary`, `ProfileController`,
`UserDirectoryController`; new in `error`: four generic exceptions. Rewritten: `Ticket`, `TicketService`,
`TicketResponse`, `TicketSpecifications`, `TicketRepository`, `AdminUserController`. Edited: `TicketStatus`,
`TicketPriority`, `TicketRequest`, `TicketListQuery`, `TicketSortField`, `TicketController`, `AppUser`,
`AppUserRepository`, `UserService`, `UserResponse`, `AuthController`, `SecurityConfig`, `GlobalExceptionHandler`,
application class, `pom.xml` (actuator), properties. Migrations V6–V12.
Backend tests: new `TestDatabase`, `ApiTestSupport`, `TicketAccessIntegrationTest`,
`AdministrationAndAssignmentIntegrationTest`, `SlaMonitorIntegrationTest`, `SlaMathTest`, `TicketAccessPolicyTest`,
`FileTypeDetectorTest`, `FilenameSanitizerTest`, `LoginAttemptLimiterTest`; rewritten `TicketServiceTest`; updated
`TicketApiIntegrationTest`, `TicketListApiIntegrationTest` (real admin via `@WithUserDetails`), `TicketControllerTest`,
`TicketStatusTest`, `DatabaseMigrationTest`, `SecurityConfigurationTest`, `AuthApiIntegrationTest`,
`RoleAuthorizationIntegrationTest`, `UserServiceTest`, `BootstrapUserRunnerTest`.
Frontend: see "Frontend changes"; new folders `admin`, `dashboard`, `inbox`, `profile`, `testing`, `e2e`.
Infra/docs: Dockerfiles, `.dockerignore`s, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`,
`.github/workflows/ci.yml`, `.gitignore`, README, CHANGES, docs/{ARCHITECTURE,SECURITY,SLA,TESTING,API,DEMO}.md.

### Tests executed and results
- **Frontend (executed, PASS):** `npm run lint` (tsc strict for app, specs, e2e + Prettier) passed; `ng build` passed
  (initial bundle ~457 kB); `ng test --watch=false`: **18 files, 125 tests, all passed** (was 101/102 at 6B).
- **Pure-Java harness (executed outside the repo, PASS):** real `SlaMath`, `SlaTarget`, `FileTypeDetector`,
  `FilenameSanitizer`, `TicketStatus`, `LoginAttemptLimiter` compiled with JDK 21 and run: 57 checks, 0 failures
  (same cases as the JUnit tests).
- **Backend static check:** `javac` over all main and test sources: only missing-dependency errors (and five follow-on
  errors that are artifacts of missing Spring/JUnit/Mockito types). This does not prove the code compiles.
- **Written, not executed:** all 292 backend test methods (29 classes); 5 Playwright E2E tests (they type-check and
  `playwright test --list` finds them; the browser download is blocked and there is no Docker daemon).
- `docker compose config` validated (and refuses to start without the required secrets). CI YAML parses.
- Failed: none observed in what was executed.

### Known issues / likeliest friction on the first real backend run
1. `./mvnw verify` on JDK 25 first. Likely spots: `@WithUserDetails(setupBefore = TEST_EXECUTION)` with the class-level
   annotation in the two ticket integration tests; `@EntityGraph` on the overridden `findAll(Specification, Pageable)`
   (Spring Data JPA 4); Hibernate accepting the dual mapping of `requester_id`/`assignee_id`/`team_id`
   (association + read-only basic column); H2 accepting `ALTER TABLE ... DROP CONSTRAINT` in V8; `ddl-auto=validate`
   on the new columns; JSON field counts (`$.length()` 16) in `TicketApiIntegrationTest`.
2. `SessionRoleRefreshFilter` + `CurrentUser` add two primary-key queries per request (account, team ids).
3. Requester-less legacy tickets are visible to staff only.
4. Agents can assign a ticket away from their own team and then lose access to it (by design, documented).

### Blocked
Backend build/tests (Maven Central, JDK 25), Docker image builds and compose up (no daemon), E2E run (no browser).

### Security limitations
See docs/SECURITY.md ("Remaining risks"): unverified backend, per-username in-memory login throttling (allows targeted
lockout), no general rate limiting, password change keeps other sessions, no AV scanning, plain-http compose.

### Next execution milestone
On a machine with JDK 25 and network: `cd backend && ./mvnw verify` and fix failures; then `docker compose up --build`,
`cd frontend && npx playwright install chromium && npm run e2e`; then capture screenshots (docs/DEMO.md).

### Learning notes (Commands 7B–18)
- **Authorization in the service layer, not the UI.** URL rules and `@PreAuthorize` answer "may this role call this
  endpoint?"; only the service knows the ticket, so ownership/team checks live there. Putting the rule in one class
  (`TicketAccessPolicy`) and using it both as SQL and as Java prevents lists and detail pages from disagreeing.
  *Trade-off:* one extra query per request for team ids.
- **404 vs 403.** Answering 404 for "exists but not yours" stops id enumeration (an IDOR defence). 403 is for actions on
  something the caller may see.
- **Workflow in the domain model.** `Ticket.changeStatus()` is the only way to change status, so no code path can skip
  the rules; side effects (SLA, activity, notifications) are applied by one component.
- **Transactions and side effects.** `Propagation.MANDATORY` makes activity/notification writes part of the business
  transaction; file deletion uses `afterCommit`, so a rollback never deletes files the database still references.
- **N+1 and aggregates.** Entity graphs fetch requester/assignee/team with the page; dashboards use `GROUP BY` instead of
  loading tickets into memory.
- **Secure uploads.** Never trust the name, extension or Content-Type from the client; check the bytes, store under a
  random key, serve as a download with `nosniff`.
- **SLA design.** Store deadlines (indexable, queryable for "overdue"), compute status at read time from a fixed rule set,
  pause by accumulating waiting time, and measure compliance only over decided targets.
- **Interview questions to prepare:** How do you prevent IDOR in a REST API? Why sessions + CSRF instead of JWT in
  localStorage? What does `@Version` do and what does a 409 mean to the client? How would you scale the login limiter
  across instances? What is the N+1 problem and how did you avoid it? Why `ddl-auto=validate` with Flyway? How is the SLA
  pause calculated, and what happens on reopen? Why are internal notes filtered in the query rather than the UI? What
  does the CSP in nginx protect against, and why did critical-CSS inlining have to be disabled? What would you test
  first if the backend had never been run?
- **Resume wording (only what exists):** "Built a full-stack ITSM help desk (Spring Boot 4, Angular 22, MySQL): role- and
  ownership-based authorization, SLA tracking with pause/reopen rules, internal notes, secure file uploads, persistent
  notifications and database-computed dashboards; Flyway migrations, Docker Compose and a GitHub Actions pipeline."
  Add "tested with JUnit/MockMvc integration tests" only after the backend suite has actually been run green.


## Verification on GitHub Actions – COMPLETED (2026-11-22, after the 7B–18 session)

The project was run manually with Docker Compose (requester / agent / admin workflow, internal-note privacy,
attachments, SLA pause and reopen) and pushed to `feature/supporthub`. CI results:

1. `mvnw` was committed without the executable bit (Windows) → `git update-index --chmod=+x backend/mvnw`.
2. First real backend run: 290 tests, 22 failing, all test-side (see docs/TESTING.md, "What the first real run found").
   Fixed: TicketRepositoryTest now uses the Flyway schema; SecurityConfigurationTest cookie tests use a fresh context
   (`@DirtiesContext`) because spring-security-test's `csrf()` replaces the shared CSRF repository; `@Qualifier` for the
   handler mapping (Actuator adds a second one); updated UserResponse field lists and OpenAPI parameter list; session
   assertion instead of a JSESSIONID cookie (MockMvc never sets it); expected 401 message after SessionRoleRefreshFilter;
   correct password for an admin-created account; OpenAPI enum test accepts inline enums. Real fix: Swagger
   `sortBy` allowable values now list all seven sort fields. Dashboard maps are keyed by enum name (same JSON).
3. Backend Dockerfile no longer uses apt-get/curl (blocked on the author's network); health check uses bash `/dev/tcp`.

**Final status: all four CI jobs green** – backend 290/290, frontend 125/125 + lint + build, Docker images built,
Playwright E2E 5/5 against docker compose. Statuses above marked IMPLEMENTED for Commands 7B–14/16–18 are now verified
by this pipeline. Remaining limitations are listed in docs/SECURITY.md and the README.

Resume wording can now include: "covered by 290 backend integration/unit tests, 125 frontend tests and Playwright
end-to-end tests in a GitHub Actions pipeline."
