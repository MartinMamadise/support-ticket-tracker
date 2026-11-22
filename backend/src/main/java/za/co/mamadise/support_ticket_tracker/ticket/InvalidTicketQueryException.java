package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.Map;

/** One or more query parameters of the ticket list request are invalid (answered with HTTP 400). */
public class InvalidTicketQueryException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidTicketQueryException(Map<String, String> fieldErrors) {
        super("Invalid query parameters");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    /** Parameter name to message. */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
