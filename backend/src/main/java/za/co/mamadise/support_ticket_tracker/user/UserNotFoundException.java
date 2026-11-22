package za.co.mamadise.support_ticket_tracker.user;

/** No account with this id. Mapped to HTTP 404. */
public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(Long id) {
        super("User " + id + " not found");
    }
}
