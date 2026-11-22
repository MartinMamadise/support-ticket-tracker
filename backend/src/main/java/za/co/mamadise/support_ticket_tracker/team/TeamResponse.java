package za.co.mamadise.support_ticket_tracker.team;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.user.UserSummary;

/** A team with its members and a ticket overview. */
@Schema(description = "A support team")
public record TeamResponse(
        Long id,
        String name,
        @Schema(nullable = true) String description,
        boolean active,
        List<UserSummary> members,
        @Schema(description = "Tickets of this team that are OPEN, IN_PROGRESS or WAITING") long activeTickets,
        @Schema(description = "Active tickets of this team with no assigned agent") long unassignedTickets,
        Instant createdAt,
        Instant updatedAt) {

    static TeamResponse from(Team team, long activeTickets, long unassignedTickets) {
        List<UserSummary> members = team.getMembers().stream()
                .sorted(Comparator.comparing(m -> m.getUsername()))
                .map(UserSummary::of)
                .toList();
        return new TeamResponse(team.getId(), team.getName(), team.getDescription(), team.isActive(), members,
                activeTickets, unassignedTickets, team.getCreatedAt(), team.getUpdatedAt());
    }
}
