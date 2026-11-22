-- Let the database reject values the application would never write (defence in depth for manual SQL and imports).
-- Kept apart from the indexes because DDL is not transactional in MySQL: if existing rows violate a rule, this
-- migration fails on its own and the index migration is unaffected.
--
-- When a TicketStatus or TicketPriority value is added, a later migration must replace the matching constraint.
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_status
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED'));

ALTER TABLE tickets ADD CONSTRAINT ck_tickets_priority
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE tickets ADD CONSTRAINT ck_tickets_version
    CHECK (version >= 0);
