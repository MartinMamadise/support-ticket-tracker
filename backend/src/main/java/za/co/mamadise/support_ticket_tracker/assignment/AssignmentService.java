package za.co.mamadise.support_ticket_tracker.assignment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.BadRequestException;
import za.co.mamadise.support_ticket_tracker.error.ConflictException;
import za.co.mamadise.support_ticket_tracker.error.ForbiddenException;
import za.co.mamadise.support_ticket_tracker.notification.NotificationService;
import za.co.mamadise.support_ticket_tracker.notification.NotificationType;
import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.team.TeamService;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketAccessPolicy;
import za.co.mamadise.support_ticket_tracker.ticket.TicketLookup;
import za.co.mamadise.support_ticket_tracker.ticket.TicketMessages;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.ticket.TicketResponse;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * Assigns tickets to agents and teams. Rules (all enforced here):
 * <ul>
 * <li>Only staff with work access on the ticket may assign it (ADMIN; agents for tickets they work on); final tickets
 * cannot be reassigned.</li>
 * <li>The assignee must be an enabled ADMIN or SUPPORT_AGENT; the team must exist and be active.</li>
 * <li>When both are given, the assignee must be a member of the team.</li>
 * </ul>
 * Every change is recorded in the assignment history and the activity timeline; the new and previous assignee are
 * notified (except when they made the change themselves).
 */
@Service
@Transactional
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final TicketLookup lookup;
    private final TicketRepository tickets;
    private final TicketAssignmentRepository history;
    private final AppUserRepository users;
    private final TeamService teams;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AssignmentService(TicketLookup lookup, TicketRepository tickets, TicketAssignmentRepository history,
            AppUserRepository users, TeamService teams, ActivityService activity, NotificationService notifications,
            CurrentUser currentUser, Clock clock) {
        this.lookup = lookup;
        this.tickets = tickets;
        this.history = history;
        this.users = users;
        this.teams = teams;
        this.activity = activity;
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public TicketResponse assign(Long ticketId, AssignmentRequest request) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        if (request.version() != null && !request.version().equals(ticket.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Ticket.class, ticketId);
        }
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("Ticket is " + ticket.getStatus() + " and can no longer be reassigned");
        }
        if (!TicketAccessPolicy.canAssign(viewer, ticket)) {
            throw new ForbiddenException("You are not allowed to assign this ticket");
        }
        Team team = request.teamId() == null ? null : teams.requireActiveTeam(request.teamId(), "teamId");
        AppUser assignee = request.assigneeId() == null ? null : requireAssignable(request.assigneeId());
        if (assignee != null && team != null && !team.hasMember(assignee.getId())) {
            throw BadRequestException.field("assigneeId", "The agent is not a member of team '" + team.getName() + "'");
        }
        Instant now = clock.instant();
        apply(ticket, assignee, team, viewer.id(), now);
        return TicketResponse.from(tickets.saveAndFlush(ticket), viewer, now);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> history(Long ticketId) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        if (!TicketAccessPolicy.hasWorkAccess(viewer, ticket)) {
            throw new ForbiddenException("Access denied");
        }
        return history.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream().map(AssignmentResponse::from).toList();
    }

    /**
     * Removes the agent from every active ticket assigned to them (used when an account is deactivated or loses its
     * staff role). The team assignment is kept, so the tickets stay in their team's queue.
     */
    public int unassignAllFrom(Long userId, Long actorId) {
        List<Ticket> assigned = tickets.findByAssigneeIdAndStatusIn(userId, TicketStatus.activeStatuses());
        Instant now = clock.instant();
        for (Ticket ticket : assigned) {
            apply(ticket, null, ticket.getTeam(), actorId, now);
        }
        if (!assigned.isEmpty()) {
            log.info("Unassigned {} active ticket(s) from account {}", assigned.size(), userId);
        }
        return assigned.size();
    }

    private void apply(Ticket ticket, AppUser assignee, Team team, Long actorId, Instant now) {
        AppUser previousAssignee = ticket.getAssignee();
        Team previousTeam = ticket.getTeam();
        boolean assigneeChanged = !Objects.equals(idOf(previousAssignee), idOf(assignee));
        boolean teamChanged = !Objects.equals(previousTeam == null ? null : previousTeam.getId(),
                team == null ? null : team.getId());
        if (!assigneeChanged && !teamChanged) {
            return;
        }
        AppUser actor = actorId == null ? null : users.getReferenceById(actorId);
        history.save(new TicketAssignment(ticket.getId(), previousAssignee, assignee, previousTeam, team, actor, now));
        ticket.setAssignee(assignee);
        ticket.setTeam(team);
        if (assigneeChanged) {
            activity.recordChange(ticket.getId(), actorId, ActivityType.ASSIGNEE_CHANGED, "assignee",
                    previousAssignee == null ? null : previousAssignee.getUsername(),
                    assignee == null ? null : assignee.getUsername());
            if (assignee != null) {
                notifications.notify(List.of(assignee.getId()), actorId, NotificationType.TICKET_ASSIGNED,
                        ticket.getId(), "Ticket " + TicketMessages.ref(ticket) + " was assigned to you");
            }
            if (previousAssignee != null) {
                notifications.notify(List.of(previousAssignee.getId()), actorId, NotificationType.TICKET_UNASSIGNED,
                        ticket.getId(), "Ticket " + TicketMessages.ref(ticket) + " is no longer assigned to you");
            }
        }
        if (teamChanged) {
            activity.recordChange(ticket.getId(), actorId, ActivityType.TEAM_CHANGED, "team",
                    previousTeam == null ? null : previousTeam.getName(), team == null ? null : team.getName());
            if (team != null && assignee == null) {
                // Nobody owns it yet: tell the team it has a new ticket in its queue.
                List<Long> members = team.getMembers().stream().filter(AppUser::isEnabled).map(AppUser::getId).toList();
                notifications.notify(members, actorId, NotificationType.TEAM_ASSIGNED, ticket.getId(),
                        "Ticket " + TicketMessages.ref(ticket) + " was assigned to team " + team.getName());
            }
        }
    }

    private AppUser requireAssignable(Long id) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> BadRequestException.field("assigneeId", "Account " + id + " does not exist"));
        if (!user.isEnabled()) {
            throw new ConflictException("Account '" + user.getUsername() + "' is deactivated and cannot receive tickets");
        }
        if (!user.isStaff()) {
            throw BadRequestException.field("assigneeId", "Tickets can only be assigned to support agents or administrators");
        }
        return user;
    }

    private static Long idOf(AppUser user) {
        return user == null ? null : user.getId();
    }
}
