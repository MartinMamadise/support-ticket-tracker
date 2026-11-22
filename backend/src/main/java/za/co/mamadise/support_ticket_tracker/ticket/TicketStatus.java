package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Lifecycle state of a ticket. The enum name is what is stored in the database and sent over the API.
 *
 * <pre>
 *   OPEN ----> IN_PROGRESS ----> RESOLVED ----> CLOSED
 *    ^  \        ^  |  ^  \        |
 *    |   \       |  v  |   \       |
 *    |    \    WAITING ----+--> (RESOLVED)
 *    |     \__________________ CANCELLED (from OPEN, IN_PROGRESS, WAITING)
 *    +--- back to queue (IN_PROGRESS) / reopen (RESOLVED)
 * </pre>
 *
 * WAITING means the support side is waiting for the requester; the resolution SLA clock is paused meanwhile.
 * CLOSED and CANCELLED are final. This enum is the single source of truth for the workflow:
 * the entity, the service and the API response all derive from {@link #allowedTransitions()}. Which of these moves a
 * particular account may make is decided by {@code TicketAccessPolicy}.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING,
    RESOLVED,
    CLOSED,
    CANCELLED;

    /** The statuses a ticket may move to from this one (not including staying in the same status). */
    public Set<TicketStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(OPEN, WAITING, RESOLVED, CANCELLED);
            case WAITING -> EnumSet.of(IN_PROGRESS, RESOLVED, CANCELLED);
            case RESOLVED -> EnumSet.of(OPEN, CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    /** {@link #allowedTransitions()} as a list in declaration order, for API responses and messages. */
    public List<TicketStatus> allowedTransitionList() {
        return List.copyOf(allowedTransitions());
    }

    public boolean canTransitionTo(TicketStatus target) {
        return allowedTransitions().contains(target);
    }

    /** True for CLOSED and CANCELLED: no further changes of any kind are allowed. */
    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }

    /** True for states in which the ticket's problem is considered dealt with (carry a resolution time). */
    public boolean isResolution() {
        return this == RESOLVED || this == CLOSED;
    }

    /** True while someone still has to work on the ticket: OPEN, IN_PROGRESS and WAITING. */
    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS || this == WAITING;
    }

    /** The active statuses, for queries. */
    public static Set<TicketStatus> activeStatuses() {
        return EnumSet.of(OPEN, IN_PROGRESS, WAITING);
    }
}
