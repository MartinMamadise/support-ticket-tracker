package za.co.mamadise.support_ticket_tracker.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;


/**
 * Dashboard figures, computed in SQL over the tickets the caller may see (so a USER sees statistics about their own
 * tickets only). {@code workload} is present for staff only.
 */
@Schema(description = "Dashboard statistics over the tickets visible to the caller")
public record DashboardResponse(
        @Schema(description = "visible = caller's own tickets (USER), work queue (SUPPORT_AGENT) or all (ADMIN)") String scope,
        Totals totals,
        // Keyed by enum name (String, not the enum type): swagger-core replaces an enum's documented schema with a
        // plain string when the enum is used as a map key, which would drop the value lists of TicketStatus etc.
        @Schema(description = "Ticket count per status name (every TicketStatus value is present)") Map<String, Long> byStatus,
        @Schema(description = "Ticket count per priority name (every TicketPriority value is present)") Map<String, Long> byPriority,
        @Schema(description = "Ticket count per category name (every TicketCategory value is present)") Map<String, Long> byCategory,
        Sla sla,
        @Schema(description = "Average calendar hours from creation to resolution, over resolved tickets; null when none")
        Double averageResolutionHours,
        @Schema(description = "Created and resolved tickets per UTC day, oldest first") List<TrendPoint> trend,
        @Schema(nullable = true, description = "Staff only") WorkloadResponse workload) {

    public record Totals(long total, long open, long inProgress, long waiting, long resolved, long closed, long cancelled,
            @Schema(description = "Active HIGH or CRITICAL") long highPriority,
            @Schema(description = "Active CRITICAL") long critical,
            @Schema(description = "Active with no assigned agent") long unassigned,
            @Schema(description = "Active and past the resolution deadline (paused tickets excluded)") long overdue) {
    }

    /**
     * Compliance is measured only over tickets that have an SLA and whose target is decided (met or missed); open
     * tickets that are still within their deadline are not counted either way. Null percentages mean "no data".
     */
    public record Sla(
            @Schema(nullable = true) Double responseCompliancePercent, long responseMeasured,
            @Schema(nullable = true) Double resolutionCompliancePercent, long resolutionMeasured,
            @Schema(description = "Active tickets past their response deadline without a response") long responseBreachedActive,
            @Schema(description = "Active tickets that are at risk (25% or less of the resolution target left)") long atRiskActive) {
    }

    public record TrendPoint(LocalDate date, long created, long resolved) {
    }
}
