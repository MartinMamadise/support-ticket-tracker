-- New allowed values: status WAITING (waiting for the requester), priority CRITICAL, and the ticket categories.
-- Kept apart from V7 because MySQL DDL is not transactional (same reasoning as V2/V3).
-- DROP CONSTRAINT for CHECK constraints needs MySQL 8.0.19 or later (H2 supports it too).
ALTER TABLE tickets DROP CONSTRAINT ck_tickets_status;
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_status
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED', 'CANCELLED'));

ALTER TABLE tickets DROP CONSTRAINT ck_tickets_priority;
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_priority
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

ALTER TABLE tickets ADD CONSTRAINT ck_tickets_category
    CHECK (category IN ('HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'EMAIL', 'OTHER'));
