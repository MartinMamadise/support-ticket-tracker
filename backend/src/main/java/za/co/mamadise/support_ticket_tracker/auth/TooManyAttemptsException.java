package za.co.mamadise.support_ticket_tracker.auth;

/** Sign-in refused because of too many recent failures for this name. HTTP 429. */
public class TooManyAttemptsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
