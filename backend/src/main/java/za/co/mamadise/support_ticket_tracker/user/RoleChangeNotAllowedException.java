package za.co.mamadise.support_ticket_tracker.user;

/** A role change that the rules forbid (for example, an administrator changing their own role). Mapped to HTTP 409. */
public class RoleChangeNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RoleChangeNotAllowedException(String message) {
        super(message);
    }
}
