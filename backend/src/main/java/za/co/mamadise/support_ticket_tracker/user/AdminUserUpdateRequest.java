package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Administrator edits an account. The username is permanent. Own role and own activation cannot be changed. */
@Schema(description = "Account changes (administrators only)")
public record AdminUserUpdateRequest(
        @NotBlank(message = "Email is required") @Size(max = 254) String email,
        @Size(max = 100, message = "Full name must be at most 100 characters") @Schema(nullable = true) String fullName,
        @NotNull(message = "Role is required") Role role,
        @NotNull(message = "enabled is required") Boolean enabled) {
}
