package za.co.mamadise.support_ticket_tracker;

import java.time.Instant;
import java.util.Map;

/** Standard error body returned by every failing API call. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors) {
}
