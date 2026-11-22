-- Role of each account (Command 7A). Exactly one role per account, stored by name; the names are the constants of
-- the Role enum (ADMIN, SUPPORT_AGENT, USER). Adding a role later needs a migration that replaces the CHECK.
--
-- Data safety: the column is added NOT NULL with DEFAULT 'USER', so every existing account becomes an ordinary USER
-- and nothing is ever promoted automatically; no row is deleted or rewritten otherwise. The default also means an
-- INSERT that forgets the role yields the least privileged role. There is no built-in administrator: grant ADMIN
-- deliberately (first start via BOOTSTRAP_USER_ROLE on an empty table, or one UPDATE by the operator, see README).
-- Written in the SQL subset shared by MySQL 8 and H2 (MODE=MySQL), like V1 and V4. Existing rows hold 'USER', so the
-- CHECK cannot fail; on MySQL before 8.0.16 it is parsed but ignored (the application validates the enum anyway).
ALTER TABLE app_users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE app_users ADD CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'SUPPORT_AGENT', 'USER'));
