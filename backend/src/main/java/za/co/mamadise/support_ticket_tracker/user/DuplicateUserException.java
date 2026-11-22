package za.co.mamadise.support_ticket_tracker.user;

import java.util.Map;

/** Username or email is already taken. Mapped to HTTP 409. Only raised when creating accounts, never at login. */
public class DuplicateUserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;

    public DuplicateUserException(Map<String, String> fieldErrors) {
        super("Username or email is already in use");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
