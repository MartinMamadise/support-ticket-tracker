package za.co.mamadise.support_ticket_tracker.team;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces a team's member list. Members must be active ADMIN or SUPPORT_AGENT accounts. */
@Schema(description = "The complete new member list of a team")
public record TeamMembersRequest(
        @NotNull(message = "userIds is required")
        @Size(max = 200, message = "A team can have at most 200 members")
        List<@NotNull Long> userIds) {
}
