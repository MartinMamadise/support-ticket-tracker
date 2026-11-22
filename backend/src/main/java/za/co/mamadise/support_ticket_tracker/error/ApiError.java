package za.co.mamadise.support_ticket_tracker.error;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Standard error body returned by every failing API call. */
@Schema(description = "Error body returned by every failing call")
public record ApiError(
        @Schema(description = "When the error occurred (UTC)", example = "2026-11-21T12:38:00.123Z") Instant timestamp,
        @Schema(description = "HTTP status code", example = "400") int status,
        @Schema(description = "HTTP reason phrase", example = "Bad Request") String error,
        @Schema(description = "What went wrong, suitable for showing to a user", example = "Validation failed") String message,
        @Schema(description = "Field or query-parameter name to message; empty when the error is not about specific fields")
        Map<String, String> fieldErrors) {
}
