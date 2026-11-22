package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A validated ticket list request: paging, sorting, filters and search.
 * Build it with {@link #parse}, which checks every raw query parameter and reports all problems at once.
 *
 * <ul>
 *   <li>{@code page} zero-based, default 0.</li>
 *   <li>{@code size} default {@value #DEFAULT_SIZE}; values above {@value #MAX_SIZE} are reduced to
 *       {@value #MAX_SIZE} (the response reports the size actually used).</li>
 *   <li>{@code sortBy} one of {@link TicketSortField}, default {@code createdAt}.</li>
 *   <li>{@code direction} {@code asc} or {@code desc}, default {@code desc} (newest first).</li>
 *   <li>{@code status}, {@code priority} one or more values (repeated or comma-separated); a ticket
 *       matches if it has any of the listed values. Different filters are combined with AND.</li>
 *   <li>{@code createdFrom}, {@code createdTo} ISO dates ({@code yyyy-MM-dd}), UTC, both inclusive.</li>
 *   <li>{@code q} case-insensitive text found in the title or description, or a ticket number
 *       (optionally written {@code #42}).</li>
 * </ul>
 *
 * @param createdFrom   inclusive lower bound on createdAt, or null
 * @param createdBefore exclusive upper bound on createdAt (the start of the day after {@code createdTo}), or null
 * @param search        trimmed search text, or null when absent
 * @param categories    category filter (empty = any)
 * @param view          pre-defined queue, see {@link TicketView}
 * @param teamId        only tickets of this team, or null
 * @param assigneeId    only tickets assigned to this account, or null
 * @param requesterId   only tickets raised by this account, or null
 */
public record TicketListQuery(
        int page,
        int size,
        TicketSortField sortBy,
        Sort.Direction direction,
        Set<TicketStatus> statuses,
        Set<TicketPriority> priorities,
        Instant createdFrom,
        Instant createdBefore,
        String search,
        Set<TicketCategory> categories,
        TicketView view,
        Long teamId,
        Long assigneeId,
        Long requesterId) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_SEARCH_LENGTH = 100;
    public static final TicketSortField DEFAULT_SORT = TicketSortField.CREATED_AT;
    public static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction.DESC;

    /** Validates the raw request parameters (any of which may be null) and builds the query. */
    public static TicketListQuery parse(String page, String size, String sortBy, String direction,
            List<String> status, List<String> priority, String createdFrom, String createdTo, String q) {
        return parse(page, size, sortBy, direction, status, priority, createdFrom, createdTo, q,
                null, null, null, null, null);
    }

    /** As above, plus category, view and the team / assignee / requester filters. */
    public static TicketListQuery parse(String page, String size, String sortBy, String direction,
            List<String> status, List<String> priority, String createdFrom, String createdTo, String q,
            List<String> category, String view, String teamId, String assigneeId, String requesterId) {
        Map<String, String> errors = new LinkedHashMap<>();

        int pageNumber = parseInt("page", page, 0, 0, errors);
        int pageSize = parseInt("size", size, DEFAULT_SIZE, 1, errors);
        pageSize = Math.min(pageSize, MAX_SIZE);

        TicketSortField sortField = DEFAULT_SORT;
        if (hasText(sortBy)) {
            sortField = TicketSortField.fromApiName(sortBy.trim()).orElse(null);
            if (sortField == null) {
                errors.put("sortBy", "Sort field must be one of: " + TicketSortField.supportedNames());
                sortField = DEFAULT_SORT;
            }
        }

        Sort.Direction sortDirection = DEFAULT_DIRECTION;
        if (hasText(direction)) {
            sortDirection = Sort.Direction.fromOptionalString(direction.trim()).orElse(null);
            if (sortDirection == null) {
                errors.put("direction", "Direction must be asc or desc");
                sortDirection = DEFAULT_DIRECTION;
            }
        }

        Set<TicketStatus> statuses = parseEnums("status", status, TicketStatus.class, errors);
        Set<TicketPriority> priorities = parseEnums("priority", priority, TicketPriority.class, errors);

        LocalDate from = parseDate("createdFrom", createdFrom, errors);
        LocalDate to = parseDate("createdTo", createdTo, errors);
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("createdTo", "createdTo must not be before createdFrom");
        }

        String search = null;
        if (hasText(q)) {
            search = q.trim();
            if (search.length() > MAX_SEARCH_LENGTH) {
                errors.put("q", "Search text must be at most " + MAX_SEARCH_LENGTH + " characters");
            }
        }

        Set<TicketCategory> categories = parseEnums("category", category, TicketCategory.class, errors);

        TicketView ticketView = TicketView.ALL;
        if (hasText(view)) {
            ticketView = TicketView.fromApiName(view).orElse(null);
            if (ticketView == null) {
                errors.put("view", "View must be one of: " + TicketView.supportedNames());
                ticketView = TicketView.ALL;
            }
        }
        Long team = parseId("teamId", teamId, errors);
        Long assignee = parseId("assigneeId", assigneeId, errors);
        Long requester = parseId("requesterId", requesterId, errors);

        if (!errors.isEmpty()) {
            throw new InvalidTicketQueryException(errors);
        }
        return new TicketListQuery(pageNumber, pageSize, sortField, sortDirection, statuses, priorities,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                search, categories, ticketView, team, assignee, requester);
    }

    /**
     * The page request, sorted by the chosen field and always by id as a tie-breaker, so that rows
     * with equal values keep a stable order and no ticket is repeated or skipped between pages.
     */
    public Pageable toPageable() {
        Sort sort = Sort.by(direction, sortBy.property());
        if (sortBy != TicketSortField.ID) {
            sort = sort.and(Sort.by(direction, TicketSortField.ID.property()));
        }
        return PageRequest.of(page, size, sort);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int parseInt(String name, String value, int defaultValue, int min, Map<String, String> errors) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min) {
                errors.put(name, name + " must be " + min + " or greater");
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.put(name, name + " must be a whole number");
            return defaultValue;
        }
    }

    private static Long parseId(String name, String value, Map<String, String> errors) {
        if (!hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < 1) {
                errors.put(name, name + " must be a positive number");
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.put(name, name + " must be a positive number");
            return null;
        }
    }

    private static <E extends Enum<E>> Set<E> parseEnums(String name, List<String> values, Class<E> type,
            Map<String, String> errors) {
        Set<E> result = EnumSet.noneOf(type);
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            try {
                result.add(Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                errors.put(name, "Invalid " + name + " '" + value.trim() + "'. Allowed: "
                        + String.join(", ", EnumSet.allOf(type).stream().map(Enum::name).toList()));
                break;
            }
        }
        return result;
    }

    private static LocalDate parseDate(String name, String value, Map<String, String> errors) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            errors.put(name, name + " must be a date in the format yyyy-MM-dd");
            return null;
        }
    }
}
