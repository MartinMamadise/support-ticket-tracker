package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import za.co.mamadise.support_ticket_tracker.auth.Viewer;

/**
 * Builds the WHERE clause for the ticket list. Every filter is optional, filters are combined with AND,
 * and everything is translated to SQL, so the database does the filtering and only one page is loaded.
 * The visibility rule ({@link TicketAccessPolicy#visibleTo}) is always ANDed in, so no filter can widen access.
 */
final class TicketSpecifications {

    private static final char ESCAPE = '\\';

    private TicketSpecifications() {
    }

    static Specification<Ticket> from(TicketListQuery query, Viewer viewer) {
        List<Specification<Ticket>> parts = new ArrayList<>();
        parts.add(TicketAccessPolicy.visibleTo(viewer));
        if (!query.statuses().isEmpty()) {
            parts.add((root, cq, cb) -> root.get("status").in(query.statuses()));
        }
        if (!query.priorities().isEmpty()) {
            parts.add((root, cq, cb) -> root.get("priority").in(query.priorities()));
        }
        if (query.categories() != null && !query.categories().isEmpty()) {
            parts.add((root, cq, cb) -> root.get("category").in(query.categories()));
        }
        if (query.createdFrom() != null) {
            parts.add((root, cq, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), query.createdFrom()));
        }
        if (query.createdBefore() != null) {
            parts.add((root, cq, cb) -> cb.lessThan(root.<Instant>get("createdAt"), query.createdBefore()));
        }
        if (query.teamId() != null) {
            parts.add((root, cq, cb) -> cb.equal(root.get("teamId"), query.teamId()));
        }
        if (query.assigneeId() != null) {
            parts.add((root, cq, cb) -> cb.equal(root.get("assigneeId"), query.assigneeId()));
        }
        if (query.requesterId() != null) {
            parts.add((root, cq, cb) -> cb.equal(root.get("requesterId"), query.requesterId()));
        }
        if (query.view() != null) {
            parts.add(view(query.view(), viewer));
        }
        if (query.search() != null) {
            parts.add(search(query.search()));
        }
        return Specification.allOf(parts);
    }

    private static Specification<Ticket> view(TicketView view, Viewer viewer) {
        return (root, cq, cb) -> switch (view) {
            case ALL -> cb.conjunction();
            case MINE -> cb.equal(root.get("requesterId"), viewer.id());
            case ASSIGNED -> cb.equal(root.get("assigneeId"), viewer.id());
            case TEAM -> viewer.teamIds().isEmpty() ? cb.disjunction() : root.get("teamId").in(viewer.teamIds());
            case UNASSIGNED -> cb.and(cb.isNull(root.get("assigneeId")),
                    root.get("status").in(TicketStatus.activeStatuses()));
        };
    }

    /**
     * Matches tickets whose title or description contains the text (case-insensitive), or whose
     * id equals the text when it is a ticket number such as {@code 42} or {@code #42}.
     * Comments and internal notes are deliberately not searched, so search can never reveal note content.
     */
    private static Specification<Ticket> search(String text) {
        return (root, cq, cb) -> {
            String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(root.get("title")), pattern, ESCAPE));
            any.add(cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
            Long id = ticketNumber(text);
            if (id != null) {
                any.add(cb.equal(root.get("id"), id));
            }
            return cb.or(any.toArray(new Predicate[0]));
        };
    }

    /** The ticket number in the text ("42" or "#42"), or null if the text is not one. */
    private static Long ticketNumber(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.isEmpty() || digits.length() > 18 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        return Long.valueOf(digits);
    }

    /** Makes %, _ and the escape character literal so user text cannot act as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
