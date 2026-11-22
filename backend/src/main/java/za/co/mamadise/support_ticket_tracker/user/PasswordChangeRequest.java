package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Own password change: the current password must be supplied. */
@Schema(description = "Change own password")
public record PasswordChangeRequest(
        @NotBlank(message = "Current password is required") @Size(max = 128) String currentPassword,
        @NotBlank(message = "New password is required") @Size(max = 128) String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[****]";
    }
}
