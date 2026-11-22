package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Duration;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;

/** SLA state of a ticket as computed by the server at {@code evaluatedAt}. Clients should not recompute it. */
@Schema(description = "SLA deadlines and status, computed by the server")
public record SlaInfo(
        @Schema(nullable = true) Instant firstResponseAt,
        @Schema(nullable = true) Instant responseDueAt,
        @Schema(nullable = true) Instant resolutionDueAt,
        SlaStatus responseStatus,
        SlaStatus resolutionStatus,
        @Schema(nullable = true, description = "Seconds until the response deadline (negative when overdue)") Long responseRemainingSeconds,
        @Schema(nullable = true, description = "Seconds until the resolution deadline (negative when overdue); frozen while paused") Long resolutionRemainingSeconds,
        @Schema(description = "True while WAITING: the resolution clock is stopped") boolean paused,
        @Schema(description = "Server time these values were computed for") Instant evaluatedAt) {

    public static SlaInfo of(Ticket ticket, Instant now) {
        TicketStatus status = ticket.getStatus();
        boolean cancelled = status == TicketStatus.CANCELLED;

        Duration responseTarget = ticket.getResponseDueAt() == null || ticket.getCreatedAt() == null ? null
                : Duration.between(ticket.getCreatedAt(), ticket.getResponseDueAt());
        SlaStatus responseStatus;
        Long responseRemaining;
        if (cancelled && ticket.getFirstResponseAt() == null) {
            responseStatus = SlaStatus.NOT_APPLICABLE;
            responseRemaining = null;
        } else {
            responseStatus = SlaMath.evaluate(ticket.getResponseDueAt(), responseTarget, ticket.getFirstResponseAt(),
                    null, now);
            responseRemaining = SlaMath.remainingSeconds(ticket.getResponseDueAt(), ticket.getFirstResponseAt(), null, now);
        }

        Duration resolutionTarget = ticket.getResolutionDueAt() == null || ticket.getSlaStartedAt() == null ? null
                : Duration.between(ticket.getSlaStartedAt(), ticket.getResolutionDueAt())
                        .minusSeconds(ticket.getSlaPausedSeconds());
        SlaStatus resolutionStatus;
        Long resolutionRemaining;
        if (cancelled) {
            resolutionStatus = SlaStatus.NOT_APPLICABLE;
            resolutionRemaining = null;
        } else {
            Instant completed = status.isResolution() ? ticket.getResolvedAt() : null;
            resolutionStatus = SlaMath.evaluate(ticket.getResolutionDueAt(), resolutionTarget, completed,
                    ticket.getSlaPausedAt(), now);
            resolutionRemaining = SlaMath.remainingSeconds(ticket.getResolutionDueAt(), completed,
                    ticket.getSlaPausedAt(), now);
        }
        return new SlaInfo(ticket.getFirstResponseAt(), ticket.getResponseDueAt(), ticket.getResolutionDueAt(),
                responseStatus, resolutionStatus, responseRemaining, resolutionRemaining,
                ticket.getSlaPausedAt() != null, now);
    }
}
