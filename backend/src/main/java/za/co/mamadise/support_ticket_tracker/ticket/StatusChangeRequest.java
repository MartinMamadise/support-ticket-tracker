package za.co.mamadise.support_ticket_tracker.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Body of {@code PUT /api/tickets/{id}/status}: move a ticket to another status without touching its other fields. */
@Schema(description = "New status, optionally with the version last seen")
public record StatusChangeRequest(
        @NotNull(message = "Status is required")
        @ValidEnum(value = TicketStatus.class,
                message = "Status must be one of: OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED, CANCELLED")
        @Schema(implementation = TicketStatus.class) String status,
        @PositiveOrZero(message = "Version must not be negative")
        @Schema(nullable = true, description = "Optional: the version last seen; stale values are rejected with 409")
        Long version) {

    TicketStatus statusValue() {
        return TicketStatus.valueOf(status);
    }
}
