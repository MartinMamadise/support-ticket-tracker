# Testing strategy

| Level | Tooling | What it proves |
|---|---|---|
| Pure unit | JUnit 5 + AssertJ | SLA maths, workflow table, permission matrix, file type detection, filename sanitising, login limiter, query parsing |
| Service | JUnit + Mockito | `TicketService` with the real policy, SLA and status-change logic (persistence mocked) |
| Web slice | `@WebMvcTest` + MockMvc | HTTP status codes, validation messages and error format of the ticket API |
| Integration | `@SpringBootTest` + MockMvc + H2 (Flyway migrations) | Real filter chain, sessions from real logins, SQL, transactions |
| Frontend unit | Vitest + Angular TestBed + HttpTestingController | Components, services, guards, interceptor, forms |
| End-to-end | Playwright | The full stack in Docker: UI workflow plus API-level security assertions |

## Security-focused suites

- `security/TicketAccessIntegrationTest`: IDOR on every ticket sub-resource, 404 for invisible tickets, staff/admin
  endpoints for ordinary users, team visibility for agents, internal notes absent from comments/activity/notifications
  for requesters, attachment upload/download/delete across accounts, disguised and dangerous uploads, CSRF on uploads,
  dashboard scoping, notification ownership.
- `assignment/AdministrationAndAssignmentIntegrationTest`: role defaults on account creation, self-demotion and
  self-deactivation refused, deactivation ends sessions and unassigns tickets, team membership rules, inactive teams,
  assignment validation and history, workload, comment edit/delete permissions, WAITING auto-resume.
- Existing suites from earlier commands: `SecurityConfigurationTest`, `AuthApiIntegrationTest`,
  `RoleAuthorizationIntegrationTest`, `MethodSecurityTest`, `RoleAssignmentRestrictionsTest`.

## Test data

`TestDatabase` empties all tables (child tables first) before each integration test class's tests and creates accounts
through `UserService` with real BCrypt hashes. All accounts use one test password; nothing resembles a production
credential. E2E creates its own accounts (`e2e.*`) through the admin API in `e2e/global-setup.ts`.

## Current results

All green on GitHub Actions (pull request `feature/supporthub`, 2026-11-22):

| Job | Result |
|---|---|
| Backend `./mvnw -B verify` | 290 tests, 0 failures, 0 errors |
| Frontend lint, `test:ci`, build | 125 tests passed; lint and production build passed |
| Docker | `docker compose config` valid; backend and frontend images built |
| End-to-end | 5 Playwright scenarios passed against `docker compose up` (MySQL 8.4, API, nginx) |

The full stack was also tested manually (requester, agent and admin in separate browsers) before the first push.

### What the first real run found

The backend suite had been written without being able to run it. Its first CI run (290 tests, 22 failures)
found only test-side problems, fixed in follow-up commits: a repository test that used a Hibernate-generated schema
instead of the migrations, a test helper that changed shared CSRF state, two `RequestMappingHandlerMapping` beans once
Actuator was added, outdated field lists, a cookie that MockMvc never produces, an unreachable error message, a wrong
password in one test, and an OpenAPI test that assumed enums are shared components. One real documentation bug was
found (the Swagger annotation listed only five of the seven sort fields). The CI job itself needed `mvnw` committed
as executable.
