package za.co.mamadise.support_ticket_tracker.ticket;

/**
 * What kind of problem a ticket is about. A fixed list (stored by name, CHECK constraint in V8): adding a category
 * needs a code change and a migration. Kept fixed on purpose so reports stay comparable over time.
 */
public enum TicketCategory {
    HARDWARE,
    SOFTWARE,
    NETWORK,
    ACCESS,
    EMAIL,
    OTHER
}
