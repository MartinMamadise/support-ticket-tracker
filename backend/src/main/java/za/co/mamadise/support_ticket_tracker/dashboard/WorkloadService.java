package za.co.mamadise.support_ticket_tracker.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.team.TeamRepository;
import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.Role;

/**
 * Workload figures for staff. A fixed number of grouped COUNT queries regardless of the number of tickets, agents or
 * teams; nothing is loaded ticket by ticket. Access (staff only) is enforced on the controller.
 */
@Service
@Transactional(readOnly = true)
public class WorkloadService {

    private static final Set<Role> STAFF = EnumSet.of(Role.ADMIN, Role.SUPPORT_AGENT);

    private final TicketRepository tickets;
    private final AppUserRepository users;
    private final TeamRepository teams;
    private final Clock clock;

    public WorkloadService(TicketRepository tickets, AppUserRepository users, TeamRepository teams, Clock clock) {
        this.tickets = tickets;
        this.users = users;
        this.teams = teams;
        this.clock = clock;
    }

    public WorkloadResponse workload() {
        Instant now = clock.instant();
        Set<TicketStatus> active = TicketStatus.activeStatuses();
        Map<Long, Long> perAgent = toMap(tickets.countByAssigneeForStatuses(active));
        Map<Long, Long> urgent = toMap(tickets.countByAssigneeForStatusesAndPriorities(active,
                EnumSet.of(TicketPriority.HIGH, TicketPriority.CRITICAL)));
        Map<Long, Long> overdue = toMap(tickets.countOverdueByAssignee(active, now));
        List<WorkloadResponse.AgentWorkload> agents = users.findEnabledByRoleIn(STAFF).stream()
                .map(u -> agent(u, perAgent, urgent, overdue))
                .sorted((a, b) -> Long.compare(b.activeTickets(), a.activeTickets()))
                .toList();

        Map<Long, Long> perTeam = toMap(tickets.countByTeamForStatuses(active));
        Map<Long, Long> teamUnassigned = toMap(tickets.countUnassignedByTeamForStatuses(active));
        Map<Long, Long> teamOverdue = toMap(tickets.countOverdueByTeam(active, now));
        List<WorkloadResponse.TeamWorkload> teamLoads = teams.findAll().stream()
                .filter(t -> t.isActive())
                .map(t -> {
                    long count = perTeam.getOrDefault(t.getId(), 0L);
                    return new WorkloadResponse.TeamWorkload(t.getId(), t.getName(), count,
                            teamUnassigned.getOrDefault(t.getId(), 0L), teamOverdue.getOrDefault(t.getId(), 0L),
                            WorkloadResponse.level(count));
                })
                .toList();
        return new WorkloadResponse(agents, teamLoads, tickets.countByAssigneeIdIsNullAndStatusIn(active));
    }

    private static WorkloadResponse.AgentWorkload agent(AppUser u, Map<Long, Long> perAgent, Map<Long, Long> urgent,
            Map<Long, Long> overdue) {
        long count = perAgent.getOrDefault(u.getId(), 0L);
        return new WorkloadResponse.AgentWorkload(u.getId(), u.getUsername(), u.displayName(), u.getRole(), count,
                urgent.getOrDefault(u.getId(), 0L), overdue.getOrDefault(u.getId(), 0L), WorkloadResponse.level(count));
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }
}
