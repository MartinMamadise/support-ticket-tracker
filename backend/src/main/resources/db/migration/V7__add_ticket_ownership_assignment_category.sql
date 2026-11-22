-- Ticket ownership, assignment and category (Commands 7B and 9).
-- Additive: existing tickets get category OTHER and no requester, assignee or team. Tickets without a requester were
-- created before accounts existed; only staff can see them (see TicketAccessPolicy).
-- Indexes are created before the foreign keys so MySQL reuses them instead of adding its own.
ALTER TABLE tickets ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'OTHER';
ALTER TABLE tickets ADD COLUMN requester_id BIGINT NULL;
ALTER TABLE tickets ADD COLUMN assignee_id BIGINT NULL;
ALTER TABLE tickets ADD COLUMN team_id BIGINT NULL;

-- "My tickets" (requester, newest first), an agent's queue and workload, a team's queue and workload.
CREATE INDEX idx_tickets_requester_created_at ON tickets (requester_id, created_at);
CREATE INDEX idx_tickets_assignee_status ON tickets (assignee_id, status);
CREATE INDEX idx_tickets_team_status ON tickets (team_id, status);

ALTER TABLE tickets ADD CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES app_users (id);
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES app_users (id);
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_team FOREIGN KEY (team_id) REFERENCES teams (id);
