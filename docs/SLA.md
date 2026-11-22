# SLA calculation

Implemented in `sla/SlaMath` (pure functions), applied by `sla/SlaService` and `Ticket`, monitored by `sla/SlaMonitor`.

## Targets

One row per priority in `sla_policies`, editable by administrators (**Administration → SLA targets**). Defaults (V10):

| Priority | First response | Resolution |
|---|---|---|
| CRITICAL | 15 min | 4 h |
| HIGH | 1 h | 8 h |
| MEDIUM | 4 h | 24 h |
| LOW | 8 h | 72 h |

A changed target applies to deadlines computed afterwards (new tickets, priority changes, reopen, resume after
waiting). Existing deadlines are not rewritten, so history stays explainable.

## Rules

- **Calendar time**, 24/7, UTC. No business hours or holidays (documented simplification).
- **Response deadline** = creation + response target. *First response* = the first public comment by staff with work
  access, or the first status change made by such staff. Never paused or restarted.
- **Resolution deadline** = start + resolution target + total time spent in `WAITING`. The start is the creation time,
  or the time of the last reopen (a reopened ticket gets a fresh resolution target).
- **WAITING** (waiting for the requester) pauses the resolution clock; a reply by the requester moves the ticket back to
  `IN_PROGRESS` automatically and resumes it.
- **Priority change** recomputes both deadlines from the stored start and paused time.
- **Status** (computed by the server for every response):
  `MET` / `BREACHED` once the target is reached (on time or late); otherwise `BREACHED` when the current time is
  strictly after the deadline, `PAUSED` while waiting, `AT_RISK` when at most 25% of the target is left, else
  `ON_TRACK`. `NOT_APPLICABLE` for cancelled tickets that never got a response and for tickets created before SLAs
  existed (no SLA is invented retroactively; reopening one starts its resolution clock).
- **Monitor** (every minute): one notification per ticket for a missed response, an approaching resolution deadline and
  a missed resolution, to the assignee or, without one, the team's active members. Flags on the ticket prevent repeats;
  reopening resets the resolution flags.

## Dashboard figures

- **Compliance** is measured over *decided* targets only: met, met late, or already overdue. Tickets still within their
  deadline are not counted either way, so an empty or young queue shows "–" rather than a misleading 100%.
- **Overdue** = active, not paused, resolution deadline in the past.
- **Average resolution** = mean of `resolution_seconds` (creation → first resolution, calendar time) over resolved and
  closed tickets the caller can see.
