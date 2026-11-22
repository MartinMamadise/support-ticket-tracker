package za.co.mamadise.support_ticket_tracker.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The complete new assignment of a ticket. Both fields are optional: null means "nobody" / "no team", so
 * {@code {}} unassigns the ticket completely. Send both to keep one of them unchanged.
 */
@Schema(description = "New assignee and team (null = none)")
public record AssignmentRequest(
        @Positive(message = "assigneeId must be positive")
        @Schema(nullable = true, description = "Active ADMIN or SUPPORT_AGENT; must be a member of the team when a team is given")
        Long assigneeId,
        @Positive(message = "teamId must be positive")
        @Schema(nullable = true, description = "Active team")
        Long teamId,
        @PositiveOrZero(message = "Version must not be negative")
        @Schema(nullable = true, description = "Optional: the ticket version last seen; stale values are rejected with 409")
        Long version) {
}
