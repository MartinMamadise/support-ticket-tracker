package za.co.mamadise.support_ticket_tracker.activity;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.activity.TicketActivity.EntityType;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketAccessPolicy;
import za.co.mamadise.support_ticket_tracker.ticket.TicketLookup;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * Writes and reads the ticket activity timeline. Recording joins the caller's transaction, so an entry exists exactly
 * when the change it describes was committed.
 */
@Service
public class ActivityService {

    private final TicketActivityRepository activities;
    private final AppUserRepository users;
    private final TicketLookup tickets;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ActivityService(TicketActivityRepository activities, AppUserRepository users, TicketLookup tickets,
            CurrentUser currentUser, Clock clock) {
        this.activities = activities;
        this.users = users;
        this.tickets = tickets;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long ticketId, Long actorId, ActivityType type) {
        record(ticketId, actorId, type, EntityType.TICKET, ticketId, null, null, null, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(Long ticketId, Long actorId, ActivityType type, String field, String oldValue,
            String newValue) {
        record(ticketId, actorId, type, EntityType.TICKET, ticketId, field, oldValue, newValue, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long ticketId, Long actorId, ActivityType type, EntityType entityType, Long entityId,
            String field, String oldValue, String newValue, boolean internal) {
        activities.save(new TicketActivity(ticketId, actorId == null ? null : users.getReferenceById(actorId), type,
                entityType, entityId, field, oldValue, newValue, internal, clock.instant()));
    }

    /** The timeline of a ticket the caller can see; staff-only entries are left out in SQL for everyone else. */
    @Transactional(readOnly = true)
    public List<ActivityResponse> list(Long ticketId) {
        Viewer viewer = currentUser.require();
        Ticket ticket = tickets.requireVisible(ticketId, viewer);
        List<TicketActivity> entries = TicketAccessPolicy.canSeeInternal(viewer, ticket)
                ? activities.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)
                : activities.findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(ticketId);
        return entries.stream().map(ActivityResponse::from).toList();
    }
}
