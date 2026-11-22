package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.sla.SlaInfo;
import za.co.mamadise.support_ticket_tracker.team.TeamSummary;
import za.co.mamadise.support_ticket_tracker.user.UserSummary;

/**
 * Ticket as returned by the API. Timestamps are UTC instants serialised as ISO-8601
 * (e.g. {@code 2026-11-21T12:38:00.123Z}). Send {@code version} back on update to
 * detect concurrent edits. {@code allowedTransitions} lists the statuses the <em>current caller</em> may move the
 * ticket to next, and {@code permissions} what else they may do; both are informational, the server checks again.
 */
@Schema(name = "TicketResponse", description = "A support ticket")
public record TicketResponse(
        @Schema(description = "Ticket number, assigned by the server", example = "42") Long id,
        @Schema(example = "Printer on floor 2 is offline") String title,
        @Schema(example = "Nothing prints since this morning; the display shows error E-14.") String description,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        @Schema(description = "Who raised the ticket; null for tickets created before accounts existed", nullable = true)
        UserSummary requester,
        @Schema(description = "Assigned support agent", nullable = true) UserSummary assignee,
        @Schema(description = "Assigned team", nullable = true) TeamSummary team,
        @Schema(description = "When the ticket was created (UTC)", example = "2026-11-21T12:38:00.123Z") Instant createdAt,
        @Schema(description = "When the ticket was last changed (UTC)", example = "2026-11-21T13:05:10.456Z") Instant updatedAt,
        @Schema(description = "When the ticket first reached RESOLVED or CLOSED (UTC); null while active or cancelled",
                nullable = true, example = "2026-11-22T09:00:00Z") Instant resolvedAt,
        @Schema(description = "Change counter for optimistic locking; send it back on update", example = "3") Long version,
        @Schema(description = "Statuses the caller may move this ticket to next (empty when none)")
        List<TicketStatus> allowedTransitions,
        SlaInfo sla,
        TicketPermissions permissions) {

    public static TicketResponse from(Ticket ticket, Viewer viewer, Instant now) {
        List<TicketStatus> transitions = ticket.getStatus().allowedTransitionList().stream()
                .filter(TicketAccessPolicy.allowedTransitions(viewer, ticket)::contains)
                .toList();
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory(),
                UserSummary.of(ticket.getRequester()),
                UserSummary.of(ticket.getAssignee()),
                TeamSummary.of(ticket.getTeam()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getVersion(),
                transitions,
                SlaInfo.of(ticket, now),
                TicketPermissions.of(viewer, ticket));
    }
}
