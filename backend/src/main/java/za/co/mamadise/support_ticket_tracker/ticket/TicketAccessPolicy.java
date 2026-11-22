package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import za.co.mamadise.support_ticket_tracker.auth.Viewer;

/**
 * The ticket permission rules, in one place. Used for SQL filtering (lists, dashboard) and for single-ticket checks,
 * so a ticket that does not appear in a list can also not be opened, commented on or downloaded from.
 *
 * <h2>Visibility</h2>
 * <ul>
 * <li>ADMIN: every ticket.</li>
 * <li>SUPPORT_AGENT: tickets assigned to them, tickets of their teams, the general queue (no agent and no team), and
 * tickets they raised themselves.</li>
 * <li>USER: only tickets they raised.</li>
 * </ul>
 * A ticket the caller may not see is reported as "not found" (404), exactly like a missing one.
 *
 * <h2>Working on a ticket</h2>
 * "Work access" (all status moves, editing every field, assignment, internal notes) belongs to ADMIN and to agents who
 * see the ticket as assignee, team member or through the general queue. A requester (any role, without work access)
 * may cancel an active ticket, reopen or close a RESOLVED one, edit the details while it is still OPEN, comment publicly
 * and attach files. Nobody changes a CLOSED or CANCELLED ticket.
 */
public final class TicketAccessPolicy {

    /** What a requester without work access may do with the status. */
    private static final Set<TicketStatus> REQUESTER_FROM_ACTIVE = EnumSet.of(TicketStatus.CANCELLED);
    private static final Set<TicketStatus> REQUESTER_FROM_RESOLVED = EnumSet.of(TicketStatus.OPEN, TicketStatus.CLOSED);

    private TicketAccessPolicy() {
    }

    /** SQL form of {@link #canView}. */
    public static Predicate visibility(Viewer viewer, Root<Ticket> root, CriteriaBuilder cb) {
        if (viewer.isAdmin()) {
            return cb.conjunction();
        }
        Predicate own = cb.equal(root.get("requesterId"), viewer.id());
        if (!viewer.isAgent()) {
            return own;
        }
        List<Predicate> any = new ArrayList<>();
        any.add(own);
        any.add(cb.equal(root.get("assigneeId"), viewer.id()));
        any.add(cb.and(cb.isNull(root.get("assigneeId")), cb.isNull(root.get("teamId"))));
        if (!viewer.teamIds().isEmpty()) {
            any.add(root.get("teamId").in(viewer.teamIds()));
        }
        return cb.or(any.toArray(new Predicate[0]));
    }

    public static Specification<Ticket> visibleTo(Viewer viewer) {
        return (root, query, cb) -> visibility(viewer, root, cb);
    }

    public static boolean canView(Viewer viewer, Ticket ticket) {
        return isRequester(viewer, ticket) || hasWorkAccess(viewer, ticket);
    }

    public static boolean isRequester(Viewer viewer, Ticket ticket) {
        return viewer.is(ticket.getRequesterId());
    }

    /** Admin, or an agent who sees the ticket as assignee, team member or through the general queue. */
    public static boolean hasWorkAccess(Viewer viewer, Ticket ticket) {
        if (viewer.isAdmin()) {
            return true;
        }
        if (!viewer.isAgent()) {
            return false;
        }
        return viewer.is(ticket.getAssigneeId()) || viewer.inTeam(ticket.getTeamId())
                || (ticket.getAssigneeId() == null && ticket.getTeamId() == null);
    }

    /** The statuses this viewer may move the ticket to now (a subset of the workflow's allowed transitions). */
    public static Set<TicketStatus> allowedTransitions(Viewer viewer, Ticket ticket) {
        Set<TicketStatus> workflow = ticket.getStatus().allowedTransitions();
        if (hasWorkAccess(viewer, ticket)) {
            return workflow;
        }
        if (!isRequester(viewer, ticket)) {
            return EnumSet.noneOf(TicketStatus.class);
        }
        Set<TicketStatus> permitted = EnumSet.noneOf(TicketStatus.class);
        permitted.addAll(ticket.getStatus() == TicketStatus.RESOLVED ? REQUESTER_FROM_RESOLVED
                : ticket.getStatus().isActive() ? REQUESTER_FROM_ACTIVE : Set.of());
        permitted.retainAll(workflow);
        return permitted;
    }

    /** Title, description, priority and category. */
    public static boolean canEditDetails(Viewer viewer, Ticket ticket) {
        if (ticket.getStatus().isFinal()) {
            return false;
        }
        return hasWorkAccess(viewer, ticket) || (isRequester(viewer, ticket) && ticket.getStatus() == TicketStatus.OPEN);
    }

    public static boolean canComment(Viewer viewer, Ticket ticket) {
        return !ticket.getStatus().isFinal() && canView(viewer, ticket);
    }

    /** Internal notes are written and read only by staff with work access, never by the requester. */
    public static boolean canSeeInternal(Viewer viewer, Ticket ticket) {
        return hasWorkAccess(viewer, ticket);
    }

    public static boolean canAddInternalNote(Viewer viewer, Ticket ticket) {
        return !ticket.getStatus().isFinal() && hasWorkAccess(viewer, ticket);
    }

    public static boolean canAssign(Viewer viewer, Ticket ticket) {
        return !ticket.getStatus().isFinal() && hasWorkAccess(viewer, ticket);
    }

    public static boolean canUpload(Viewer viewer, Ticket ticket) {
        return canComment(viewer, ticket);
    }

    public static boolean canDelete(Viewer viewer) {
        return viewer.isAdmin();
    }
}
