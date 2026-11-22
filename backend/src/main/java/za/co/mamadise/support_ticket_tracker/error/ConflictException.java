package za.co.mamadise.support_ticket_tracker.error;

/** The request is valid but not possible in the current state (e.g. assigning to an inactive team). HTTP 409. */
public class ConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
