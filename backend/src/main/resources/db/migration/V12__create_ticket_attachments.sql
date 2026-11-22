-- Ticket attachments (Command 13). Only metadata is stored here; the bytes live in the attachment directory
-- (ATTACHMENTS_DIR), outside anything the web server serves, under a random storage_key, never the client's name.
CREATE TABLE ticket_attachments (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id    BIGINT       NOT NULL,
    uploader_id  BIGINT       NOT NULL,
    -- Sanitised display name only; never used to build a file path.
    filename     VARCHAR(255) NOT NULL,
    -- Detected by the server from the file content, not taken from the client.
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(64)  NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_ticket_attachments PRIMARY KEY (id),
    CONSTRAINT uk_ticket_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_attachments_uploader FOREIGN KEY (uploader_id) REFERENCES app_users (id),
    CONSTRAINT ck_ticket_attachments_size CHECK (size_bytes > 0)
);
CREATE INDEX idx_ticket_attachments_ticket ON ticket_attachments (ticket_id, created_at);
