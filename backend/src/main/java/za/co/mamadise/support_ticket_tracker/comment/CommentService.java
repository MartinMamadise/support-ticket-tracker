package za.co.mamadise.support_ticket_tracker.comment;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.activity.TicketActivity.EntityType;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.ConflictException;
import za.co.mamadise.support_ticket_tracker.error.ForbiddenException;
import za.co.mamadise.support_ticket_tracker.error.NotFoundException;
import za.co.mamadise.support_ticket_tracker.notification.NotificationService;
import za.co.mamadise.support_ticket_tracker.notification.NotificationType;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketAccessPolicy;
import za.co.mamadise.support_ticket_tracker.ticket.TicketLookup;
import za.co.mamadise.support_ticket_tracker.ticket.TicketMessages;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatusChanger;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * Comments and internal notes.
 * <ul>
 * <li>Anyone who can see a ticket may read its public comments and add one while the ticket is not final.</li>
 * <li>Internal notes are read and written only by staff with work access. For everyone else they are excluded by the
 * query itself, and asking for one by id answers 404 (it "does not exist" for them).</li>
 * <li>Authors may edit their own comment while the ticket is not final; only administrators may delete one.</li>
 * <li>A public reply by staff counts as the first response for the SLA. A public reply by the requester to a WAITING
 * ticket moves it back to IN_PROGRESS.</li>
 * </ul>
 * Notifications about comments say that a comment was added; they never contain the text.
 */
@Service
@Transactional
public class CommentService {

    private final TicketCommentRepository comments;
    private final TicketLookup lookup;
    private final TicketRepository tickets;
    private final TicketStatusChanger statusChanger;
    private final AppUserRepository users;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final Clock clock;

    public CommentService(TicketCommentRepository comments, TicketLookup lookup, TicketRepository tickets,
            TicketStatusChanger statusChanger, AppUserRepository users, ActivityService activity,
            NotificationService notifications, CurrentUser currentUser, Clock clock) {
        this.comments = comments;
        this.lookup = lookup;
        this.tickets = tickets;
        this.statusChanger = statusChanger;
        this.users = users;
        this.activity = activity;
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long ticketId) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        List<TicketComment> found = TicketAccessPolicy.canSeeInternal(viewer, ticket)
                ? comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)
                : comments.findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(ticketId);
        return found.stream().map(c -> CommentResponse.from(c, canEdit(viewer, ticket, c))).toList();
    }

    public CommentResponse add(Long ticketId, CommentRequest request) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("Ticket is " + ticket.getStatus() + " and no longer accepts comments");
        }
        boolean internal = request.isInternal();
        if (internal && !TicketAccessPolicy.canAddInternalNote(viewer, ticket)) {
            throw new ForbiddenException("Only support staff working on this ticket can add internal notes");
        }
        Instant now = clock.instant();
        TicketComment saved = comments.saveAndFlush(new TicketComment(ticketId, users.getReferenceById(viewer.id()),
                request.body().trim(), internal, now));
        activity.record(ticketId, viewer.id(), internal ? ActivityType.INTERNAL_NOTE_ADDED : ActivityType.COMMENT_ADDED,
                EntityType.COMMENT, saved.getId(), null, null, null, internal);

        boolean staff = TicketAccessPolicy.hasWorkAccess(viewer, ticket);
        if (internal) {
            // Staff only: the assignee hears about notes; the requester never does.
            notifications.notify(java.util.Collections.singletonList(ticket.getAssigneeId()), viewer.id(),
                    NotificationType.INTERNAL_NOTE_ADDED, ticketId, "Internal note added on ticket " + TicketMessages.ref(ticket));
        } else {
            if (staff) {
                ticket.recordFirstResponse(now);
            } else if (TicketAccessPolicy.isRequester(viewer, ticket) && ticket.getStatus() == TicketStatus.WAITING) {
                statusChanger.changeAutomatically(ticket, TicketStatus.IN_PROGRESS, viewer.id(), now);
            }
            List<Long> recipients = new ArrayList<>();
            recipients.add(ticket.getRequesterId());
            recipients.add(ticket.getAssigneeId());
            notifications.notify(recipients, viewer.id(), NotificationType.COMMENT_ADDED, ticketId,
                    "New comment on ticket " + TicketMessages.ref(ticket));
            tickets.save(ticket);
        }
        return CommentResponse.from(saved, true);
    }

    public CommentResponse edit(Long ticketId, Long commentId, CommentRequest request) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        TicketComment comment = findVisible(viewer, ticket, commentId);
        if (!canEdit(viewer, ticket, comment)) {
            throw new ForbiddenException("You can only edit your own comments on tickets that are not closed");
        }
        comment.edit(request.body().trim(), clock.instant());
        activity.record(ticketId, viewer.id(), ActivityType.COMMENT_EDITED, EntityType.COMMENT, commentId, null, null,
                null, comment.isInternal());
        return CommentResponse.from(comments.saveAndFlush(comment), true);
    }

    public void delete(Long ticketId, Long commentId) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        TicketComment comment = findVisible(viewer, ticket, commentId);
        if (!viewer.isAdmin()) {
            throw new ForbiddenException("Only administrators can delete comments");
        }
        comments.delete(comment);
        activity.record(ticketId, viewer.id(), ActivityType.COMMENT_DELETED, EntityType.COMMENT, commentId, null, null,
                null, comment.isInternal());
    }

    private TicketComment findVisible(Viewer viewer, Ticket ticket, Long commentId) {
        TicketComment comment = comments.findByIdAndTicketId(commentId, ticket.getId())
                .orElseThrow(() -> new NotFoundException("Comment " + commentId + " not found"));
        if (comment.isInternal() && !TicketAccessPolicy.canSeeInternal(viewer, ticket)) {
            throw new NotFoundException("Comment " + commentId + " not found");
        }
        return comment;
    }

    private static boolean canEdit(Viewer viewer, Ticket ticket, TicketComment comment) {
        return !ticket.getStatus().isFinal() && viewer.is(comment.getAuthor().getId())
                && (!comment.isInternal() || TicketAccessPolicy.canSeeInternal(viewer, ticket));
    }
}
