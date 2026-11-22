package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Administrator creates an account. The role is optional and defaults to the least privileged {@link Role#USER}, so
 * forgetting it can never grant privileges. Field rules (username/email/password) are checked by UserService.
 */
@Schema(description = "New account (administrators only)")
public record AdminUserCreateRequest(
        @NotBlank(message = "Username is required") @Size(max = 50) @Schema(example = "thandi") String username,
        @NotBlank(message = "Email is required") @Size(max = 254) @Schema(example = "thandi@example.com") String email,
        @Size(max = 100, message = "Full name must be at most 100 characters") @Schema(nullable = true) String fullName,
        @NotBlank(message = "Password is required") @Size(max = 128)
        @Schema(description = "Initial password: at least 10 characters with a letter and a digit") String password,
        @Schema(nullable = true, description = "Defaults to USER") Role role) {

    @Override
    public String toString() {
        return "AdminUserCreateRequest[username=" + username + ", role=" + role + ", password=****]";
    }
}
