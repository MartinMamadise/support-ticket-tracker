-- Indexes for GET /api/tickets (filters, default and chosen sort order, paging).
-- InnoDB appends the primary key to every secondary index, so each index below also serves the "id" tie-breaker
-- that every list query ends with.

-- Default list (newest first, no filter), the createdFrom/createdTo range filter and sortBy=createdAt.
CREATE INDEX idx_tickets_created_at ON tickets (created_at);

-- status filter combined with the default sort. Its leftmost column also covers a status-only filter.
CREATE INDEX idx_tickets_status_created_at ON tickets (status, created_at);

-- priority filter combined with the default sort.
CREATE INDEX idx_tickets_priority_created_at ON tickets (priority, created_at);

-- sortBy=updatedAt.
CREATE INDEX idx_tickets_updated_at ON tickets (updated_at);

-- Not indexed on purpose: sorting by status/priority orders by a CASE rank (cannot use a plain index), and the
-- title/description search is a substring LIKE with a leading wildcard (cannot use a B-tree index).
