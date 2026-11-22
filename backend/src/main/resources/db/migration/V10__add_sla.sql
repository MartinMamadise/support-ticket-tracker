-- SLA management (Command 11). Targets per priority, in minutes of calendar time (24/7, no business hours).
-- The seeded values are defaults an administrator can change through the API.
CREATE TABLE sla_policies (
    priority           VARCHAR(20) NOT NULL,
    response_minutes   INT         NOT NULL,
    resolution_minutes INT         NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT pk_sla_policies PRIMARY KEY (priority),
    CONSTRAINT ck_sla_policies_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_sla_policies_response CHECK (response_minutes > 0),
    CONSTRAINT ck_sla_policies_resolution CHECK (resolution_minutes >= response_minutes)
);

INSERT INTO sla_policies (priority, response_minutes, resolution_minutes, updated_at) VALUES
    ('CRITICAL', 15, 240, CURRENT_TIMESTAMP),
    ('HIGH', 60, 480, CURRENT_TIMESTAMP),
    ('MEDIUM', 240, 1440, CURRENT_TIMESTAMP),
    ('LOW', 480, 4320, CURRENT_TIMESTAMP);

-- Per-ticket SLA state. All NULL for tickets created before this migration: no SLA is invented for them
-- (the API reports NOT_APPLICABLE) until they are reopened, which starts a fresh resolution clock.
ALTER TABLE tickets ADD COLUMN sla_started_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN first_response_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN response_due_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN resolution_due_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN resolution_warning_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN sla_paused_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN sla_paused_seconds BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD COLUMN response_breach_notified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tickets ADD COLUMN resolution_warning_notified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tickets ADD COLUMN resolution_breach_notified BOOLEAN NOT NULL DEFAULT FALSE;
-- Calendar seconds from creation to resolution (set with resolved_at), so averages are a plain AVG in SQL.
ALTER TABLE tickets ADD COLUMN resolution_seconds BIGINT NULL;

-- The SLA monitor and the "overdue" dashboard figures search by deadline.
CREATE INDEX idx_tickets_resolution_due_at ON tickets (resolution_due_at);
CREATE INDEX idx_tickets_response_due_at ON tickets (response_due_at);
