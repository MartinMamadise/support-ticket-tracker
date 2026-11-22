package za.co.mamadise.support_ticket_tracker.ticket;

import org.springframework.stereotype.Component;

import za.co.mamadise.support_ticket_tracker.auth.Viewer;

/**
 * Loads a single ticket for an operation and applies the visibility rule. Every feature that works on "ticket X"
 * (comments, notes, activity, attachments, assignment) goes through here, so access is checked the same way everywhere.
 * Missing and invisible tickets both give {@link TicketNotFoundException} (404), so ticket ids cannot be probed.
 */
@Component
public class TicketLookup {

    private final TicketRepository tickets;

    public TicketLookup(TicketRepository tickets) {
        this.tickets = tickets;
    }

    /** Call inside a transaction; the returned entity is managed. */
    public Ticket requireVisible(Long id, Viewer viewer) {
        Ticket ticket = tickets.findDetailedById(id).orElseThrow(() -> new TicketNotFoundException(id));
        if (!TicketAccessPolicy.canView(viewer, ticket)) {
            throw new TicketNotFoundException(id);
        }
        return ticket;
    }
}
