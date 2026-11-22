package za.co.mamadise.support_ticket_tracker.team;

import io.swagger.v3.oas.annotations.media.Schema;

/** Short reference to a team, used inside other responses. */
@Schema(description = "Short reference to a team")
public record TeamSummary(@Schema(example = "3") Long id, @Schema(example = "Service Desk") String name) {

    public static TeamSummary of(Team team) {
        return team == null ? null : new TeamSummary(team.getId(), team.getName());
    }
}
