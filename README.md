# Support Ticket Tracker

A web application for logging and managing support requests. Staff can create tickets, track their
status and priority, update them as work progresses, and delete them when no longer needed.

The project has two parts that live in this repository:

| Folder      | What it is                                  |
|-------------|---------------------------------------------|
| `backend/`  | Spring Boot REST API backed by MySQL        |
| `frontend/` | Angular single-page application             |

## Features

- List all tickets, newest first, with status, priority and last-updated time
- Search by title or ticket number, and filter by status
- Create, view, edit and delete tickets
- Status: Open, In progress, Resolved, Closed. Priority: Low, Medium, High
- Server-side validation with field-level error messages shown on the form
- Clear feedback after every action (created / updated / deleted / error), plus loading,
  empty and "server unreachable" states
- Consistent JSON error responses with correct HTTP status codes (400, 404, 405, 500)

## Technology

- **Backend:** Java 25, Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA (Hibernate), Bean Validation, MySQL 8, Maven
- **Frontend:** Angular 22, TypeScript, plain CSS (no UI framework), Vitest
- **Backend tests:** JUnit 5, Mockito, Spring MockMvc, H2 (in-memory, tests only)

## Architecture

```
Angular (port 4200)  --/api proxy-->  Spring Boot (port 8080)  -->  MySQL
```

Backend (package `za.co.mamadise.support_ticket_tracker`):

| Class                    | Responsibility                                                     |
|--------------------------|--------------------------------------------------------------------|
| `TicketController`       | REST endpoints, request validation (`@Valid`), HTTP status codes   |
| `TicketService`          | Business logic, transactions, "not found" handling                 |
| `TicketRepository`       | Spring Data JPA access to the `tickets` table                      |
| `Ticket`                 | JPA entity; timestamps set automatically on insert/update          |
| `TicketRequest`          | Validated request body (keeps clients from setting id/timestamps)  |
| `GlobalExceptionHandler` | Turns exceptions into a consistent `ApiError` JSON body            |

Frontend (`frontend/src/app`): `tickets/` holds the list, detail and form screens plus the API
service; `shared/` holds the status/priority badge and the notification service.

### REST API

| Method | Path               | Success            | Errors                       |
|--------|--------------------|--------------------|------------------------------|
| GET    | `/api/tickets`     | 200 list           |                              |
| GET    | `/api/tickets/{id}`| 200 ticket         | 400 bad id, 404              |
| POST   | `/api/tickets`     | 201 + `Location`   | 400 validation / bad JSON    |
| PUT    | `/api/tickets/{id}`| 200 ticket         | 400, 404                     |
| DELETE | `/api/tickets/{id}`| 204                | 400 bad id, 404              |

Request body for POST/PUT:

```json
{ "title": "VPN down", "description": "Cannot connect", "status": "OPEN", "priority": "HIGH" }
```

`title` (max 150) and `description` (max 2000) are required; `priority` is required
(`LOW`, `MEDIUM`, `HIGH`); `status` is optional (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`) and defaults
to `OPEN` on create / is left unchanged on update.

Error body:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Validation failed",
  "fieldErrors": { "title": "Title is required" } }
```

## Prerequisites

- JDK 25
- MySQL 8.0 running locally
- Node.js 22 or later and npm
- Maven is not required separately - the project includes the Maven wrapper (`mvnw`)

## Setup

### 1. Create the database

```sql
CREATE DATABASE support_ticket_tracker CHARACTER SET utf8mb4;
```

The `tickets` table is created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).

### 2. Configure database credentials

Credentials are read from environment variables and are never stored in the repository.
See `.env.example` for the full list.

| Variable      | Required | Default                  |
|---------------|----------|--------------------------|
| `DB_USERNAME` | yes      |                          |
| `DB_PASSWORD` | yes      |                          |
| `DB_HOST`     | no       | `localhost`              |
| `DB_PORT`     | no       | `3306`                   |
| `DB_NAME`     | no       | `support_ticket_tracker` |

Windows PowerShell (current session):

```powershell
$env:DB_USERNAME = "your_user"
$env:DB_PASSWORD = "your_password"
```

macOS / Linux:

```bash
export DB_USERNAME=your_user
export DB_PASSWORD=your_password
```

If you run from an IDE, set the same variables in the run configuration.

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The API is now at <http://localhost:8080/api/tickets>.

### 4. Run the frontend

```bash
cd frontend
npm install
npm start
```

Open <http://localhost:4200>. The dev server forwards `/api` requests to `localhost:8080`
(see `frontend/proxy.conf.json`), so no CORS configuration is needed during development.

### Upgrading a database created by an earlier version

Earlier versions never filled in `created_at` / `updated_at`. If your database already has tickets, run:

```sql
UPDATE tickets SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
```

Also make sure existing `status` values are one of `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` and
`priority` values are one of `LOW`, `MEDIUM`, `HIGH` (the API now rejects anything else when saving).
Alternatively, drop and recreate the database if it only contains test data.

## Testing

Backend (needs no MySQL - tests use an in-memory H2 database):

```bash
cd backend
./mvnw clean test
```

| Test class                    | Covers                                                                            |
|-------------------------------|-----------------------------------------------------------------------------------|
| `TicketRepositoryTest`        | Save, find, find by status, delete, automatic timestamps                          |
| `TicketServiceTest`           | Create defaults, trimming, update, "not found" for get/update/delete (Mockito)    |
| `TicketControllerTest`        | Status codes, validation messages, bad JSON, bad id, 404s, 405 (service mocked)   |
| `TicketApiIntegrationTest`    | Full CRUD flow through real controller, service, repository and database          |
| `SupportTicketTrackerApplicationTests` | Application context starts                                               |

Frontend:

```bash
cd frontend
npm test          # unit tests (Vitest)
npm run build     # production build into frontend/dist
```

## Production notes

`npm run build` produces static files in `frontend/dist/frontend/browser`. Serve them from a web
server that forwards `/api` to the Spring Boot application (or add a CORS configuration to the
backend if they are hosted on different origins). `ddl-auto=update` is convenient for development;
for a real deployment, manage the schema with a migration tool such as Flyway.

## Possible future work

Authentication and user accounts, ticket assignment, comments/history, pagination for large datasets.
