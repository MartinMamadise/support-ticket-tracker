package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The fields clients may sort the ticket list by. {@link #apiName()} is the value of the
 * {@code sortBy} query parameter; {@link #property()} is the entity attribute the database sorts on.
 * Only these values are accepted, so a client can never sort by an arbitrary property.
 *
 * Priority and status sort by their meaning, not alphabetically: LOW &lt; MEDIUM &lt; HIGH &lt; CRITICAL and
 * OPEN &lt; IN_PROGRESS &lt; WAITING &lt; RESOLVED &lt; CLOSED &lt; CANCELLED (see the rank formulas on {@link Ticket}).
 * {@code resolutionDueAt} puts tickets without an SLA first in ascending order on MySQL and H2 (NULLs sort low).
 */
public enum TicketSortField {
    ID("id", "id"),
    CREATED_AT("createdAt", "createdAt"),
    UPDATED_AT("updatedAt", "updatedAt"),
    PRIORITY("priority", "priorityRank"),
    STATUS("status", "statusRank"),
    CATEGORY("category", "category"),
    RESOLUTION_DUE_AT("resolutionDueAt", "resolutionDueAt");

    private final String apiName;
    private final String property;

    TicketSortField(String apiName, String property) {
        this.apiName = apiName;
        this.property = property;
    }

    public String apiName() {
        return apiName;
    }

    public String property() {
        return property;
    }

    /** Case-insensitive lookup by API name. */
    public static Optional<TicketSortField> fromApiName(String value) {
        return Arrays.stream(values()).filter(f -> f.apiName.equalsIgnoreCase(value)).findFirst();
    }

    /** The accepted values, for error messages. */
    public static String supportedNames() {
        return Arrays.stream(values()).map(f -> f.apiName).collect(Collectors.joining(", "));
    }
}
