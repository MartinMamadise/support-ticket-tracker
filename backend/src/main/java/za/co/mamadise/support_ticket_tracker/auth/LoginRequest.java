package za.co.mamadise.support_ticket_tracker.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials. {@code toString} is overridden so the password can never end up in a log line. */
@Schema(description = "Login credentials")
public record LoginRequest(
        @Schema(description = "Username, or the account's email address", example = "thandi")
        @NotBlank(message = "Username is required")
        @Size(max = 254, message = "Username must be at most 254 characters") String username,
        @Schema(description = "Password", example = "correct horse 42", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must be at most 128 characters") String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
