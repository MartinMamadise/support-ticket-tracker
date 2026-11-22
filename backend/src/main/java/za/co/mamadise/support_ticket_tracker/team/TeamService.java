package za.co.mamadise.support_ticket_tracker.team;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.error.BadRequestException;
import za.co.mamadise.support_ticket_tracker.error.ConflictException;
import za.co.mamadise.support_ticket_tracker.error.NotFoundException;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/** Team listing (staff) and team administration (administrators; enforced on the controllers). */
@Service
@Transactional
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teams;
    private final AppUserRepository users;
    private final TicketRepository tickets;

    public TeamService(TeamRepository teams, AppUserRepository users, TicketRepository tickets) {
        this.teams = teams;
        this.users = users;
        this.tickets = tickets;
    }

    /** Every team (or only active ones) with members and ticket counts; two aggregate queries for the counts. */
    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams(boolean includeInactive) {
        Map<Long, Long> active = toMap(tickets.countByTeamForStatuses(TicketStatus.activeStatuses()));
        Map<Long, Long> unassigned = toMap(tickets.countUnassignedByTeamForStatuses(TicketStatus.activeStatuses()));
        return teams.findAllWithMembers().stream()
                .filter(t -> includeInactive || t.isActive())
                .map(t -> TeamResponse.from(t, active.getOrDefault(t.getId(), 0L), unassigned.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(Long id) {
        Team team = find(id);
        Map<Long, Long> active = toMap(tickets.countByTeamForStatuses(TicketStatus.activeStatuses()));
        Map<Long, Long> unassigned = toMap(tickets.countUnassignedByTeamForStatuses(TicketStatus.activeStatuses()));
        return TeamResponse.from(team, active.getOrDefault(id, 0L), unassigned.getOrDefault(id, 0L));
    }

    public TeamResponse createTeam(TeamRequest request) {
        String name = request.name().trim();
        if (teams.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A team with this name already exists");
        }
        Team team = new Team(name, blankToNull(request.description()));
        team.setActive(request.active() == null || request.active());
        Team saved = teams.saveAndFlush(team);
        log.info("Team {} created", saved.getId());
        return TeamResponse.from(saved, 0, 0);
    }

    public TeamResponse updateTeam(Long id, TeamRequest request) {
        Team team = find(id);
        String name = request.name().trim();
        if (teams.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A team with this name already exists");
        }
        team.setName(name);
        team.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            team.setActive(request.active());
        }
        teams.saveAndFlush(team);
        return getTeam(id);
    }

    /** Replaces the member list. Only active staff can be members (they are the ones who work on tickets). */
    public TeamResponse setMembers(Long id, TeamMembersRequest request) {
        Team team = find(id);
        Set<Long> ids = new LinkedHashSet<>(request.userIds());
        List<AppUser> found = users.findAllById(ids);
        Set<Long> foundIds = found.stream().map(AppUser::getId).collect(Collectors.toSet());
        Set<Long> missing = new HashSet<>(ids);
        missing.removeAll(foundIds);
        if (!missing.isEmpty()) {
            throw BadRequestException.field("userIds", "Unknown account id(s): " + missing);
        }
        List<Long> notStaff = found.stream().filter(u -> !u.isStaff() || !u.isEnabled()).map(AppUser::getId).toList();
        if (!notStaff.isEmpty()) {
            throw BadRequestException.field("userIds",
                    "Only active administrators and support agents can be team members: " + notStaff);
        }
        team.getMembers().clear();
        team.getMembers().addAll(found);
        teams.saveAndFlush(team);
        log.info("Team {} now has {} member(s)", id, found.size());
        return getTeam(id);
    }

    /** For other services: an existing, active team, or a 400 naming the field. */
    @Transactional(readOnly = true)
    public Team requireActiveTeam(Long id, String field) {
        Team team = teams.findWithMembersById(id)
                .orElseThrow(() -> BadRequestException.field(field, "Team " + id + " does not exist"));
        if (!team.isActive()) {
            throw new ConflictException("Team '" + team.getName() + "' is inactive and cannot receive tickets");
        }
        return team;
    }

    private Team find(Long id) {
        return teams.findWithMembersById(id).orElseThrow(() -> new NotFoundException("Team " + id + " not found"));
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
