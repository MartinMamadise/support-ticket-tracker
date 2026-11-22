package za.co.mamadise.support_ticket_tracker.ticket;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(Long id) {
        super("Ticket not found with ID: " + id);
    }
}
