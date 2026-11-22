package za.co.mamadise.support_ticket_tracker.user;

import java.util.Map;

/** The account details are not acceptable. Mapped to HTTP 400 with one message per field. */
public class InvalidUserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;

    public InvalidUserException(Map<String, String> fieldErrors) {
        super("Invalid account details");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
