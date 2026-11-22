-- Accounts that can sign in to the application. Additive: no existing table or row is touched.
-- Written in the SQL subset shared by MySQL 8 and H2 (MODE=MySQL), like V1.
--
-- username and email are stored normalised (trimmed, lower case) by the application, so the plain UNIQUE keys
-- below are effectively case-insensitive on every database. Each UNIQUE constraint also creates the index that
-- serves the login lookup by username or by email, so no further index is needed.
-- password_hash only ever holds a BCrypt hash (60 characters; 100 leaves room for a stronger scheme later).
-- No account is created here: there is no default or built-in user.
CREATE TABLE app_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uk_app_users_username UNIQUE (username),
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_username_not_blank CHECK (CHAR_LENGTH(username) >= 3),
    CONSTRAINT ck_app_users_password_hash_not_blank CHECK (CHAR_LENGTH(password_hash) >= 20)
);
