# Demo walkthrough (about 5 minutes)

Preparation: `docker compose up --build`, sign in as the bootstrap admin, then under **Administration**:
create a team "Service Desk", an agent `sam` (Support agent, member of Service Desk) and a user `una` (User).

1. **Requester view** – sign in as `una`, raise "Laptop does not charge" (High, Hardware). Point out: only *Dashboard,
   New ticket, My tickets* in the navigation; the ticket shows SLA deadlines computed by the server.
2. **Agent view** – sign in as `sam`: the ticket is in *Unassigned*. Open it, *Assign to me*, *Start work* (first
   response recorded, response SLA "Met").
3. **Internal note vs reply** – add an internal note ("waiting for charger stock") and a public reply asking for the
   serial number, then *Wait for requester*: the resolution clock shows "Paused".
4. **Back to the requester** – as `una`: bell shows unread notifications; the reply is visible, the note is not.
   Reply with the serial number → the ticket moves back to *In progress* automatically.
5. **Resolve and reopen** – as `sam`, attach a PDF, *Resolve*. As `una`, *Reopen* (fresh resolution target), then
   show the activity timeline.
6. **Security in one minute** – as `una`, open `/admin/users` (redirected) and another user's ticket URL ("not found").
   In Swagger or the browser dev tools show `GET /api/admin/users` → 403.
7. **Manager view** – as admin: dashboard KPIs, SLA compliance, trend, agent workload; change the HIGH SLA target.

## Screenshots to capture (`docs/screenshots/`)

`dashboard-agent.png`, `ticket-list-filters.png`, `ticket-detail-internal-note.png`, `ticket-detail-requester.png`
(same ticket, note hidden), `admin-users.png`, `admin-team-members.png`, `sla-targets.png`, `notifications.png`.
