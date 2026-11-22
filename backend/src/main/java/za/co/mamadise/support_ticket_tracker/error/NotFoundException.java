package za.co.mamadise.support_ticket_tracker.error;

/**
 * Something does not exist, or exists but the caller may not know about it. Mapped to HTTP 404. Use it for both
 * cases, so that a forbidden resource cannot be told apart from a missing one (no enumeration of other people's data).
 */
public class NotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
