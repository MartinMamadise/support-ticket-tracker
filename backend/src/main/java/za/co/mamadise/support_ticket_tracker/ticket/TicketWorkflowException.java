package za.co.mamadise.support_ticket_tracker.ticket;

/** A change was rejected because it breaks the ticket workflow rules. Mapped to HTTP 409. */
public class TicketWorkflowException extends RuntimeException {

    private TicketWorkflowException(String message) {
        super(message);
    }

    public static TicketWorkflowException invalidTransition(TicketStatus from, TicketStatus to) {
        String allowed = from.isFinal()
                ? "none, the ticket is final"
                : from.allowedTransitionList().toString();
        return new TicketWorkflowException(
                "Cannot change ticket status from " + from + " to " + to + ". Allowed: " + allowed + ".");
    }

    public static TicketWorkflowException invalidInitialStatus(TicketStatus requested) {
        return new TicketWorkflowException(
                "New tickets must start as OPEN, but status " + requested + " was requested.");
    }

    public static TicketWorkflowException finalTicketNotEditable(TicketStatus status) {
        return new TicketWorkflowException(
                "Ticket is " + status + " and can no longer be edited.");
    }
}
