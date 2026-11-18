package za.co.mamadise.support_ticket_tracker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a ticket.
 * Keeping this separate from the entity means clients cannot set the id or timestamps.
 */
public record TicketRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        /** Optional: defaults to OPEN on create and is left unchanged on update. */
        @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED",
                message = "Status must be one of: OPEN, IN_PROGRESS, RESOLVED, CLOSED")
        String status,

        @NotNull(message = "Priority is required")
        @Pattern(regexp = "LOW|MEDIUM|HIGH",
                message = "Priority must be one of: LOW, MEDIUM, HIGH")
        String priority) {
}
