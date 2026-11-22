package za.co.mamadise.support_ticket_tracker.user;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of the administrator's role change. An unknown role name fails JSON parsing (400); null fails validation. */
@Schema(description = "The new role for an account")
public record RoleChangeRequest(
        @NotNull @Schema(description = "ADMIN, SUPPORT_AGENT or USER", example = "SUPPORT_AGENT") Role role) {
}
