package za.co.mamadise.support_ticket_tracker.ticket;

/**
 * How urgent a ticket is. The enum name is what is stored in the database and sent over the API.
 * Each priority has its own SLA targets ({@code sla.SlaPolicy}).
 */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** HIGH and CRITICAL: shown separately on the dashboard. */
    public boolean isUrgent() {
        return this == HIGH || this == CRITICAL;
    }
}
