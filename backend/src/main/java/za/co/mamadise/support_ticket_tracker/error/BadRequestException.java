package za.co.mamadise.support_ticket_tracker.error;

import java.util.Map;

/** Invalid input that bean validation cannot express (cross-field rules, lookups). HTTP 400 with field messages. */
public class BadRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Map<String, String> fieldErrors;

    public BadRequestException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static BadRequestException field(String field, String message) {
        return new BadRequestException("Validation failed", Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
