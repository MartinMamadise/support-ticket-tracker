# API overview

Base path `/api`. JSON in and out (attachments: `multipart/form-data`). Authentication by session cookie; every
`POST`/`PUT`/`DELETE` needs the `X-XSRF-TOKEN` header. The full, generated reference is the OpenAPI document
(`/v3/api-docs`, Swagger UI at `/swagger-ui.html`) with `SPRING_PROFILES_ACTIVE=dev`.

## Conventions

- Errors: `{ "timestamp", "status", "error", "message", "fieldErrors": { field: message } }`.
- 400 validation, 401 not signed in, 403 signed in but not allowed (or CSRF), 404 missing **or not visible**,
  409 conflict (workflow, stale `version`, duplicates, inactive targets), 413 upload too large, 429 too many sign-in
  failures.
- Paged lists: `?page=0&size=20` (max 100) → `{content, page, size, totalElements, totalPages, hasNext, hasPrevious}`.
- Timestamps: ISO-8601 UTC instants.

## Endpoints

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/auth/csrf` | anyone | Sets the XSRF-TOKEN cookie |
| POST | `/auth/login` · `/auth/logout` | anyone · signed in | Start / end the session |
| GET | `/auth/me` | signed in | Current account |
| GET, PUT | `/profile` · PUT `/profile/password` | signed in | Own name/email; own password |
| GET | `/tickets` | signed in | Visible tickets; filters `status, priority, category, view, teamId, assigneeId, requesterId, createdFrom, createdTo, q`, sort `sortBy, direction` |
| POST | `/tickets` | signed in | Raise a ticket (caller becomes requester) |
| GET, PUT, DELETE | `/tickets/{id}` | per policy; DELETE admin | Read, edit, delete |
| PUT | `/tickets/{id}/status` | per policy | Status change (`allowedTransitions` in the response) |
| PUT | `/tickets/{id}/assignment` · GET `/tickets/{id}/assignments` | staff with work access | Assign; history |
| GET, POST | `/tickets/{id}/comments` | per policy | List / add (`internal: true` for staff notes) |
| PUT, DELETE | `/tickets/{id}/comments/{commentId}` | author · admin | Edit own / delete |
| GET | `/tickets/{id}/activity` | per policy | Timeline (staff-only entries filtered) |
| GET, POST | `/tickets/{id}/attachments` | per policy | List / upload (`file` part) |
| GET | `/tickets/{id}/attachments/{aid}/download` | per policy | Download |
| DELETE | `/tickets/{id}/attachments/{aid}` | uploader · admin | Remove |
| GET | `/notifications` · `/notifications/unread-count` | signed in | Own notifications |
| PUT | `/notifications/{id}/read` · `/notifications/read-all` | signed in | Mark read |
| GET | `/dashboard?trendDays=14` | signed in | Statistics over visible tickets |
| GET | `/workload` | staff | Agent and team workload |
| GET | `/teams`, `/teams/{id}` | staff | Teams with members and counts |
| GET | `/users/assignable?teamId=` | staff | Active staff for assignment |
| GET, POST | `/admin/users` · GET, PUT `/admin/users/{id}` | admin | Search, create, read, edit |
| PUT | `/admin/users/{id}/password` · `/admin/users/{id}/role` | admin | Reset password; change role |
| POST | `/admin/teams` · PUT `/admin/teams/{id}` · PUT `/admin/teams/{id}/members` | admin | Team management |
| GET | `/admin/sla-policies` · PUT `/admin/sla-policies/{priority}` | admin | SLA targets |
| GET | `/actuator/health` | anyone | UP/DOWN incl. database (no details) |

"Per policy" = `TicketAccessPolicy`, summarised in the README's permission table.
