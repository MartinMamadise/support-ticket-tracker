-- Teams and team membership (Command 8), plus the optional display name of an account.
-- Additive only: no existing row is changed. Written in the SQL subset shared by MySQL 8 and H2 (MODE=MySQL).
--
-- Team names are unique. MySQL's default collation compares case-insensitively; the application also checks
-- case-insensitively before saving, so H2 behaves the same.
-- Membership rows disappear with their team or account (ON DELETE CASCADE); accounts are normally deactivated,
-- never deleted, so in practice only team membership edits remove rows.
ALTER TABLE app_users ADD COLUMN full_name VARCHAR(100) NULL;

CREATE TABLE teams (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(500) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_teams PRIMARY KEY (id),
    CONSTRAINT uk_teams_name UNIQUE (name),
    CONSTRAINT ck_teams_name_not_blank CHECK (CHAR_LENGTH(name) >= 2)
);

CREATE TABLE team_members (
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_team_members PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

-- "Which teams is this account in?" is asked on every ticket request by a support agent.
CREATE INDEX idx_team_members_user ON team_members (user_id);
