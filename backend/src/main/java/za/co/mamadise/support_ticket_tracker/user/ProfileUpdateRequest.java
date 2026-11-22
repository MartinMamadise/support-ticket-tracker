package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What anyone may change about their own account: display name and email. Not username, role or status. */
@Schema(description = "Own profile changes")
public record ProfileUpdateRequest(
        @NotBlank(message = "Email is required") @Size(max = 254) String email,
        @Size(max = 100, message = "Full name must be at most 100 characters") @Schema(nullable = true) String fullName) {
}
