package za.co.mamadise.support_ticket_tracker.team;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating or editing a team (administrators). */
@Schema(description = "Team name, description and whether it is active")
public record TeamRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be 2-80 characters")
        @Schema(example = "Service Desk") String name,
        @Size(max = 500, message = "Description must be at most 500 characters")
        @Schema(nullable = true, example = "First-line support for all staff") String description,
        @Schema(description = "Defaults to true on create; false deactivates the team (no new tickets)", nullable = true)
        Boolean active) {
}
