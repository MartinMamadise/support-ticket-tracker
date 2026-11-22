package za.co.mamadise.support_ticket_tracker.assignment;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.team.TeamSummary;
import za.co.mamadise.support_ticket_tracker.user.UserSummary;

@Schema(description = "One entry of a ticket's assignment history")
public record AssignmentResponse(
        Long id,
        @Schema(nullable = true) UserSummary previousAssignee,
        @Schema(nullable = true) UserSummary newAssignee,
        @Schema(nullable = true) TeamSummary previousTeam,
        @Schema(nullable = true) TeamSummary newTeam,
        @Schema(nullable = true, description = "Who changed it; null for automatic changes") UserSummary actor,
        Instant createdAt) {

    static AssignmentResponse from(TicketAssignment a) {
        return new AssignmentResponse(a.getId(), UserSummary.of(a.getPreviousAssignee()),
                UserSummary.of(a.getNewAssignee()), TeamSummary.of(a.getPreviousTeam()), TeamSummary.of(a.getNewTeam()),
                UserSummary.of(a.getActor()), a.getCreatedAt());
    }
}
