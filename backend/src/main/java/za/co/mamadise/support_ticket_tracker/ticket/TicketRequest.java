package za.co.mamadise.support_ticket_tracker.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a ticket.
 * Keeping this separate from the entity means clients cannot set the id or timestamps.
 * Status and priority arrive as strings and are checked against the enums by {@link ValidEnum}.
 */
@Schema(name = "TicketRequest", description = "Body for creating or updating a ticket")
public record TicketRequest(

        @Schema(description = "Short summary of the problem", example = "Printer on floor 2 is offline")
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Schema(description = "Full description of the problem", example = "Nothing prints since this morning; the display shows error E-14.")
        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        /**
         * Optional. On create it must be OPEN (the default); on update it must be the current status
         * or one of its allowed transitions (see {@link TicketStatus}), and is left unchanged if omitted.
         */
        @Schema(implementation = TicketStatus.class, nullable = true,
                description = "Optional. On create it must be OPEN. On update: the current status or one of the ticket's "
                        + "`allowedTransitions`; the status is unchanged when omitted.")
        @ValidEnum(value = TicketStatus.class,
                message = "Status must be one of: OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED, CANCELLED")
        String status,

        @Schema(implementation = TicketPriority.class, description = "How urgent the ticket is")
        @NotNull(message = "Priority is required")
        @ValidEnum(value = TicketPriority.class,
                message = "Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL")
        String priority,

        @Schema(implementation = TicketCategory.class, nullable = true,
                description = "Optional. Defaults to OTHER on create; unchanged on update when omitted.")
        @ValidEnum(value = TicketCategory.class,
                message = "Category must be one of: HARDWARE, SOFTWARE, NETWORK, ACCESS, EMAIL, OTHER")
        String category,

        /**
         * Optional, update only: the {@code version} the client last saw. If it no longer matches
         * the stored ticket the update is rejected with 409 instead of overwriting newer changes.
         */
        @Schema(nullable = true, example = "3",
                description = "Update only, optional: the `version` last seen by the client. A stale value is rejected with 409.")
        @PositiveOrZero(message = "Version must not be negative")
        Long version) {

    /** The requested status, or null when the client did not send one. Call only after validation. */
    TicketStatus statusOrNull() {
        return status == null ? null : TicketStatus.valueOf(status);
    }

    /** The requested category, or null when the client did not send one. Call only after validation. */
    TicketCategory categoryOrNull() {
        return category == null ? null : TicketCategory.valueOf(category);
    }

    /** The requested priority. Call only after validation. */
    TicketPriority priorityValue() {
        return TicketPriority.valueOf(priority);
    }
}
