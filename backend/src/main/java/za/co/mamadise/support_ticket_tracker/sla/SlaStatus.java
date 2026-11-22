package za.co.mamadise.support_ticket_tracker.sla;

/** State of one SLA target (response or resolution) of a ticket, always computed by the backend. */
public enum SlaStatus {
    /** Running, more than 25% of the target left. */
    ON_TRACK,
    /** Running, at most 25% of the target left. */
    AT_RISK,
    /** Deadline passed without the target being met, or met late. */
    BREACHED,
    /** Met on time. */
    MET,
    /** Resolution clock stopped while the ticket is WAITING for the requester. */
    PAUSED,
    /** No SLA applies: ticket created before SLAs existed, or cancelled before the target was reached. */
    NOT_APPLICABLE
}
