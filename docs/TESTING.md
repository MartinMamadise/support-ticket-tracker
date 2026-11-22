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

See `AI_PROGRESS.md` for the latest executed results. At the end of the 7B–18 session: frontend 125/125 unit tests
passed, lint and production build passed; 292 backend test methods written or updated but **not executed** (no Maven
access); 57 checks of the pure-Java classes passed in a scratch harness; E2E suite type-checks and lists 5 tests but
was not run (no browser or Docker daemon).
