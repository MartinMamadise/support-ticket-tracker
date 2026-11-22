package za.co.mamadise.support_ticket_tracker.dashboard;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.user.Role;

/** Active-ticket workload per agent and per team. "Active" = OPEN, IN_PROGRESS or WAITING. */
@Schema(description = "Workload per agent and team")
public record WorkloadResponse(
        List<AgentWorkload> agents,
        List<TeamWorkload> teams,
        @Schema(description = "Active tickets with no assigned agent") long unassignedTickets) {

    /** LOW below 5 active tickets, MEDIUM below 10, HIGH from 10. */
    public enum Level { LOW, MEDIUM, HIGH }

    public static Level level(long active) {
        return active >= 10 ? Level.HIGH : active >= 5 ? Level.MEDIUM : Level.LOW;
    }

    public record AgentWorkload(Long userId, String username, String displayName, Role role, long activeTickets,
            @Schema(description = "Active HIGH and CRITICAL tickets") long urgentTickets,
            @Schema(description = "Active tickets past their resolution deadline") long overdueTickets, Level level) {
    }

    public record TeamWorkload(Long teamId, String name, long activeTickets, long unassignedTickets, long overdueTickets,
            Level level) {
    }
}
