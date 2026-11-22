package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.ForbiddenException;
import za.co.mamadise.support_ticket_tracker.notification.NotificationService;
import za.co.mamadise.support_ticket_tracker.notification.NotificationType;
import za.co.mamadise.support_ticket_tracker.sla.SlaService;

/**
 * Performs a status change with everything that belongs to it: permission check, workflow check (in the entity), SLA
 * bookkeeping, first-response tracking, activity entry and notifications. Shared by the ticket endpoints and by
 * automatic moves (a requester replying to a WAITING ticket), so all of them behave identically.
 */
@Component
public class TicketStatusChanger {

    private final SlaService sla;
    private final ActivityService activity;
    private final NotificationService notifications;

    public TicketStatusChanger(SlaService sla, ActivityService activity, NotificationService notifications) {
        this.sla = sla;
        this.activity = activity;
        this.notifications = notifications;
    }

    /**
     * Changes the status as {@code actor}. A move the workflow forbids is a 409 ({@link TicketWorkflowException}); a
     * move the workflow allows but this actor may not make is a 403. Re-sending the current status is a no-op.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void change(Ticket ticket, TicketStatus target, Viewer actor, Instant now) {
        TicketStatus from = ticket.getStatus();
        if (target == from) {
            return;
        }
        if (from.canTransitionTo(target) && !TicketAccessPolicy.allowedTransitions(actor, ticket).contains(target)) {
            throw new ForbiddenException("You are not allowed to move this ticket to " + target);
        }
        apply(ticket, target, actor.id(), TicketAccessPolicy.hasWorkAccess(actor, ticket), now);
    }

    /** A move made by the system on behalf of {@code actorId} (already authorised by the caller). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void changeAutomatically(Ticket ticket, TicketStatus target, Long actorId, Instant now) {
        if (ticket.getStatus() != target) {
            apply(ticket, target, actorId, false, now);
        }
    }

    private void apply(Ticket ticket, TicketStatus target, Long actorId, boolean byStaff, Instant now) {
        TicketStatus from = ticket.getStatus();
        ticket.changeStatus(target, now); // throws TicketWorkflowException for forbidden moves, before any change
        sla.onStatusChanged(ticket, from, target, now);
        if (byStaff) {
            ticket.recordFirstResponse(now); // the first staff action on the ticket counts as the response
        }
        ActivityType type = switch (target) {
            case RESOLVED -> ActivityType.RESOLVED;
            case CLOSED -> ActivityType.CLOSED;
            case CANCELLED -> ActivityType.CANCELLED;
            case OPEN -> from.isResolution() ? ActivityType.REOPENED : ActivityType.STATUS_CHANGED;
            default -> ActivityType.STATUS_CHANGED;
        };
        activity.recordChange(ticket.getId(), actorId, type, "status", from.name(), target.name());

        NotificationType notificationType = type == ActivityType.RESOLVED ? NotificationType.TICKET_RESOLVED
                : type == ActivityType.REOPENED ? NotificationType.TICKET_REOPENED : NotificationType.STATUS_CHANGED;
        String text = switch (notificationType) {
            case TICKET_RESOLVED -> "Ticket " + TicketMessages.ref(ticket) + " was resolved";
            case TICKET_REOPENED -> "Ticket " + TicketMessages.ref(ticket) + " was reopened";
            default -> "Ticket " + TicketMessages.ref(ticket) + " is now " + target;
        };
        List<Long> recipients = new ArrayList<>();
        recipients.add(ticket.getRequesterId());
        recipients.add(ticket.getAssigneeId());
        notifications.notify(recipients, actorId, notificationType, ticket.getId(), text);
    }
}
