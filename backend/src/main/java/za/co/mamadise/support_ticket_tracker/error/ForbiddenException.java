package za.co.mamadise.support_ticket_tracker.error;

/**
 * The caller can see the resource but may not perform this operation on it (e.g. a requester trying to add an internal
 * note). HTTP 403. The message is fixed text that reveals nothing beyond what the caller can already see.
 */
public class ForbiddenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
