package za.co.mamadise.support_ticket_tracker.auth;

/** Sign-in failed or the session is no longer valid. Mapped to HTTP 401; the message is safe to show. */
public class AuthenticationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
