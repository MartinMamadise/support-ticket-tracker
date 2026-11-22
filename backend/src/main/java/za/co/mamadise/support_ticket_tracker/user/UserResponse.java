package za.co.mamadise.support_ticket_tracker.user;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** The only full representation of an account that leaves the backend. It has no password and no hash. */
@Schema(description = "An account (never contains a password or hash; the role is safe to show)")
public record UserResponse(
        @Schema(description = "Account id", example = "1") Long id,
        @Schema(description = "Unique login name (lower case)", example = "thandi") String username,
        @Schema(description = "Email address (lower case)", example = "thandi@example.com") String email,
        @Schema(description = "Optional display name", nullable = true, example = "Thandi Mokoena") String fullName,
        @Schema(description = "The account's single role", example = "USER") Role role,
        @Schema(description = "False when the account is deactivated and cannot sign in") boolean enabled,
        @Schema(description = "When the account was created (UTC)") Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}
