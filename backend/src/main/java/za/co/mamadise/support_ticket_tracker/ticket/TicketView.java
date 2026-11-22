package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pre-defined ticket queues for the {@code view} list parameter. Always combined with the visibility rules, so a view
 * can only narrow what the caller may see.
 */
public enum TicketView {
    /** Everything the caller may see (default). */
    ALL,
    /** Tickets the caller raised. */
    MINE,
    /** Tickets assigned to the caller. */
    ASSIGNED,
    /** Tickets of the caller's teams. */
    TEAM,
    /** Active tickets with no assigned agent. */
    UNASSIGNED;

    public static Optional<TicketView> fromApiName(String value) {
        return Arrays.stream(values()).filter(v -> v.name().equalsIgnoreCase(value.trim())).findFirst();
    }

    public static String supportedNames() {
        return Arrays.stream(values()).map(v -> v.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
    }
}
