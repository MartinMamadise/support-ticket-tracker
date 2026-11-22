package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Administrator sets a new password for an account. */
@Schema(description = "New password set by an administrator")
public record PasswordResetRequest(@NotBlank(message = "Password is required") @Size(max = 128) String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[****]";
    }
}
