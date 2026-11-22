package za.co.mamadise.support_ticket_tracker.activity;

/** What happened to a ticket. Stored by name in ticket_activity.activity_type. */
public enum ActivityType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    RESOLVED,
    REOPENED,
    CLOSED,
    CANCELLED,
    PRIORITY_CHANGED,
    CATEGORY_CHANGED,
    ASSIGNEE_CHANGED,
    TEAM_CHANGED,
    COMMENT_ADDED,
    COMMENT_EDITED,
    COMMENT_DELETED,
    INTERNAL_NOTE_ADDED,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED,
    SLA_RESPONSE_BREACHED,
    SLA_RESOLUTION_BREACHED
}
