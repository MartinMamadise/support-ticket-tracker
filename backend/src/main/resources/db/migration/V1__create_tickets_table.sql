-- Baseline schema of the ticket tracker, as defined by the Ticket entity.
-- Written in the SQL subset shared by MySQL 8 and H2 (MODE=MySQL) so the same file runs in production and in tests.
--
-- Column names follow Spring Boot's naming strategy (createdAt -> created_at).
-- status and priority are plain VARCHAR holding the enum names (Ticket maps them with SqlTypes.VARCHAR), so adding
-- an enum value never needs an ALTER of the column type. Timestamps are UTC instants with microsecond precision.
CREATE TABLE tickets (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(150)  NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    priority    VARCHAR(20)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    resolved_at DATETIME(6)   NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_tickets PRIMARY KEY (id)
);
