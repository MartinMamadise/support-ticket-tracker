package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** Minimal public view of an account, used inside other responses (requester, assignee, author). No email. */
@Schema(description = "Short reference to an account")
public record UserSummary(
        @Schema(example = "7") Long id,
        @Schema(example = "thandi") String username,
        @Schema(description = "Full name, or the username when none is set", example = "Thandi Mokoena") String displayName) {

    /** Null-safe: returns null for a null account. Works on lazy proxies (only reads loaded columns). */
    public static UserSummary of(AppUser user) {
        return user == null ? null : new UserSummary(user.getId(), user.getUsername(), user.displayName());
    }
}
