-- Ticket communication and history (Commands 9 and 10). All rows belong to a ticket and are removed with it.
-- Authors and actors are accounts; accounts are deactivated rather than deleted, so those keys use the default
-- RESTRICT behaviour. actor_id is nullable for changes made by the system (e.g. the SLA monitor).

CREATE TABLE ticket_comments (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id  BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    body       VARCHAR(5000) NOT NULL,
    -- TRUE = internal note: visible to ADMIN and SUPPORT_AGENT only, never to the requester.
    internal   BOOLEAN       NOT NULL DEFAULT FALSE,
    edited     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    CONSTRAINT pk_ticket_comments PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES app_users (id)
);
CREATE INDEX idx_ticket_comments_ticket_created ON ticket_comments (ticket_id, created_at);

CREATE TABLE ticket_activity (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT       NOT NULL,
    actor_id      BIGINT       NULL,
    activity_type VARCHAR(40)  NOT NULL,
    entity_type   VARCHAR(20)  NOT NULL,
    entity_id     BIGINT       NULL,
    field_name    VARCHAR(40)  NULL,
    old_value     VARCHAR(255) NULL,
    new_value     VARCHAR(255) NULL,
    -- TRUE for events only staff may see (internal notes); filtered out for requesters by the API.
    internal      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_ticket_activity PRIMARY KEY (id),
    CONSTRAINT fk_ticket_activity_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_activity_actor FOREIGN KEY (actor_id) REFERENCES app_users (id)
);
CREATE INDEX idx_ticket_activity_ticket_created ON ticket_activity (ticket_id, created_at);

CREATE TABLE ticket_assignments (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id            BIGINT      NOT NULL,
    previous_assignee_id BIGINT      NULL,
    new_assignee_id      BIGINT      NULL,
    previous_team_id     BIGINT      NULL,
    new_team_id          BIGINT      NULL,
    actor_id             BIGINT      NULL,
    created_at           DATETIME(6) NOT NULL,
    CONSTRAINT pk_ticket_assignments PRIMARY KEY (id),
    CONSTRAINT fk_ticket_assignments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_assignments_prev_assignee FOREIGN KEY (previous_assignee_id) REFERENCES app_users (id),
    CONSTRAINT fk_ticket_assignments_new_assignee FOREIGN KEY (new_assignee_id) REFERENCES app_users (id),
    CONSTRAINT fk_ticket_assignments_prev_team FOREIGN KEY (previous_team_id) REFERENCES teams (id),
    CONSTRAINT fk_ticket_assignments_new_team FOREIGN KEY (new_team_id) REFERENCES teams (id),
    CONSTRAINT fk_ticket_assignments_actor FOREIGN KEY (actor_id) REFERENCES app_users (id)
);
CREATE INDEX idx_ticket_assignments_ticket_created ON ticket_assignments (ticket_id, created_at);
