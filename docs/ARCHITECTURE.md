# Architecture

## Components

```mermaid
flowchart TB
    subgraph Browser
      SPA[Angular SPA<br/>signals, standalone components]
    end
    subgraph "frontend container"
      NG[nginx<br/>static files, security headers,<br/>/api reverse proxy]
    end
    subgraph "backend container"
      F[Security filter chain<br/>session, CSRF, role refresh]
      C[Controllers<br/>DTOs, validation, OpenAPI]
      S[Services<br/>TicketAccessPolicy, workflow, SLA]
      R[Repositories<br/>JPA, Specifications, aggregates]
      M[SLA monitor<br/>@Scheduled]
    end
    DB[(MySQL 8)]
    FS[[attachments volume]]
    SPA -->|same origin| NG --> F --> C --> S --> R --> DB
    S --> FS
    M --> S
```

The browser only ever talks to one origin (nginx in Docker, the Angular dev-server proxy locally), so the session and
CSRF cookies work without CORS.

## Backend packages

| Package | Responsibility |
|---|---|
| `auth` | Security configuration, login/logout/me, CSRF bootstrap, role annotations, `CurrentUser` / `Viewer`, login limiter |
| `user` | Accounts, roles, password policy, admin user management, own profile, staff lookup |
| `team` | Teams and membership |
| `ticket` | Ticket entity and workflow, `TicketAccessPolicy`, list query/specifications, ticket service and API |
| `assignment` | Assigning agents/teams, assignment history |
| `comment` | Public comments and internal notes |
| `activity` | Activity timeline (written by the other services) |
| `sla` | SLA policies, `SlaMath`, per-ticket SLA bookkeeping, scheduled monitor |
| `notification` | Persistent in-app notifications and the `NotificationChannel` extension point |
| `attachment` | Upload validation, storage abstraction (`AttachmentStorage`), authorised download |
| `dashboard` | Aggregate statistics and workload |
| `error` | `GlobalExceptionHandler`, `ApiError` and shared exceptions |

### Key design decisions

- **One permission policy.** `TicketAccessPolicy` defines who may see and do what with a ticket. Its `visibility()`
  method builds a JPA `Predicate`, so the same rule filters lists and dashboard queries in SQL; its boolean methods
  check single tickets. `TicketLookup` loads a ticket for any sub-resource (comments, activity, attachments,
  assignment) and applies it, so no endpoint can forget the check.
- **404 instead of 403 for invisible tickets.** A ticket you may not see behaves exactly like one that does not exist,
  so ids cannot be probed. 403 is used only where the caller can see the resource but not perform the action.
- **The workflow lives in the entity.** `Ticket.changeStatus()` is the only way to change status and rejects
  transitions the workflow forbids (409). `TicketStatusChanger` adds the side effects (permissions, SLA, activity,
  notifications) and is shared by the API and automatic moves.
- **Dual mapping of foreign keys.** Requester, assignee and team are lazy associations *and* read-only id columns;
  queries filter on the id columns (no joins), changes go through the associations; the list query fetches the
  associations with an entity graph (no N+1).
- **Aggregates in SQL.** Dashboard and workload use `COUNT`/`AVG ... GROUP BY`; only the trend loads a single
  timestamp column for at most 90 days.
- **Side effects in the same transaction.** Activity entries and notifications are written with
  `Propagation.MANDATORY`, so they exist exactly when the change they describe was committed. Attachment files are
  deleted after commit, and removed again if the insert rolls back.

## Data model

```mermaid
erDiagram
    app_users ||--o{ tickets : "requests (requester_id)"
    app_users ||--o{ tickets : "works (assignee_id)"
    teams ||--o{ tickets : "queue (team_id)"
    teams ||--o{ team_members : has
    app_users ||--o{ team_members : "member of"
    tickets ||--o{ ticket_comments : has
    tickets ||--o{ ticket_activity : has
    tickets ||--o{ ticket_assignments : has
    tickets ||--o{ ticket_attachments : has
    tickets ||--o{ notifications : about
    app_users ||--o{ notifications : receives
    sla_policies ||..o{ tickets : "targets by priority"

    app_users {
      bigint id PK
      varchar username UK
      varchar email UK
      varchar password_hash
      varchar full_name
      varchar role "ADMIN | SUPPORT_AGENT | USER"
      boolean enabled
    }
    tickets {
      bigint id PK
      varchar title
      varchar status
      varchar priority
      varchar category
      bigint requester_id FK
      bigint assignee_id FK
      bigint team_id FK
      datetime created_at
      datetime resolved_at
      datetime response_due_at
      datetime resolution_due_at
      datetime sla_paused_at
      bigint version "optimistic lock"
    }
    teams { bigint id PK
      varchar name UK
      boolean active }
    team_members { bigint team_id PK
      bigint user_id PK }
    ticket_comments { bigint id PK
      bigint ticket_id FK
      bigint author_id FK
      varchar body
      boolean internal }
    ticket_activity { bigint id PK
      bigint ticket_id FK
      bigint actor_id FK
      varchar activity_type
      varchar old_value
      varchar new_value
      boolean internal }
    ticket_assignments { bigint id PK
      bigint ticket_id FK
      bigint previous_assignee_id FK
      bigint new_assignee_id FK
      bigint previous_team_id FK
      bigint new_team_id FK
      bigint actor_id FK }
    ticket_attachments { bigint id PK
      bigint ticket_id FK
      varchar filename
      varchar content_type
      varchar storage_key UK
      char sha256 }
    notifications { bigint id PK
      bigint recipient_id FK
      bigint ticket_id FK
      varchar notification_type
      datetime read_at }
    sla_policies { varchar priority PK
      int response_minutes
      int resolution_minutes }
```

Children of a ticket are removed with it (`ON DELETE CASCADE`); accounts are deactivated, never deleted. Status,
priority, category and role are `VARCHAR` with `CHECK` constraints. All timestamps are UTC (`DATETIME(6)`).

## Authentication flow

```mermaid
sequenceDiagram
    participant B as Browser (Angular)
    participant A as API
    B->>A: GET /api/auth/csrf
    A-->>B: 204 + Set-Cookie XSRF-TOKEN
    B->>A: POST /api/auth/login {username, password} + X-XSRF-TOKEN
    A->>A: limiter check, BCrypt verify, change session id
    A-->>B: 200 user + Set-Cookie JSESSIONID (HttpOnly)
    B->>A: GET /api/tickets (cookie)
    A->>A: SessionRoleRefreshFilter re-reads account (disabled → 401, role changed → new authorities)
    A->>A: CurrentUser → Viewer(id, role, teamIds) → TicketAccessPolicy
    A-->>B: 200 page of visible tickets
    B->>A: POST /api/auth/logout + X-XSRF-TOKEN
    A-->>B: 204, session invalidated
```

On a 401 from any API call the Angular interceptor ends the local session and redirects to `/login?returnUrl=…`;
on the "Invalid or missing CSRF token" 403 it fetches a token and retries once.

## Frontend structure

`src/app/` is organised by feature: `auth` (login, guards, interceptor), `dashboard`, `tickets` (list, detail, form,
API, models), `admin` (users, teams, SLA), `profile`, `inbox` (persistent notifications), `shared` (badges, charts,
confirmation dialog, toasts, formatting). State is held in signals inside components and small root services; there is
no global store because no state is shared widely enough to need one. All data comes from the API; the UI never
computes permissions or SLA status itself (it renders `permissions`, `allowedTransitions` and `sla` from the server).
