package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/** Validation and defaults of the ticket list query parameters (no Spring context needed). */
class TicketListQueryTest {

    private static TicketListQuery parse(String page, String size, String sortBy, String direction,
            List<String> status, List<String> priority, String from, String to, String q) {
        return TicketListQuery.parse(page, size, sortBy, direction, status, priority, from, to, q);
    }

    private static TicketListQuery defaults() {
        return parse(null, null, null, null, null, null, null, null, null);
    }

    private static Map<String, String> errorsOf(Runnable call) {
        try {
            call.run();
        } catch (InvalidTicketQueryException e) {
            return e.getFieldErrors();
        }
        throw new AssertionError("Expected InvalidTicketQueryException");
    }

    @Test
    void usesDefaultsWhenNothingIsSent() {
        TicketListQuery query = defaults();

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sortBy()).isEqualTo(TicketSortField.CREATED_AT);
        assertThat(query.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(query.statuses()).isEmpty();
        assertThat(query.priorities()).isEmpty();
        assertThat(query.createdFrom()).isNull();
        assertThat(query.createdBefore()).isNull();
        assertThat(query.search()).isNull();
    }

    @Test
    void blankParametersAreTreatedAsMissing() {
        TicketListQuery query = parse(" ", "", " ", "", List.of(""), List.of(" "), "", " ", "   ");

        assertThat(query).isEqualTo(defaults());
    }

    @Test
    void acceptsCustomPageAndSize() {
        TicketListQuery query = parse("3", "50", null, null, null, null, null, null, null);

        assertThat(query.page()).isEqualTo(3);
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    void reducesOversizedPageSizeToTheMaximum() {
        assertThat(parse(null, "100", null, null, null, null, null, null, null).size()).isEqualTo(100);
        assertThat(parse(null, "101", null, null, null, null, null, null, null).size()).isEqualTo(100);
        assertThat(parse(null, "2147483647", null, null, null, null, null, null, null).size())
                .isEqualTo(TicketListQuery.MAX_SIZE);
    }

    @Test
    void rejectsNegativePageZeroSizeAndNonNumbers() {
        assertThat(errorsOf(() -> parse("-1", null, null, null, null, null, null, null, null)))
                .containsOnlyKeys("page");
        assertThat(errorsOf(() -> parse(null, "0", null, null, null, null, null, null, null)))
                .containsOnlyKeys("size");
        assertThat(errorsOf(() -> parse(null, "-5", null, null, null, null, null, null, null)))
                .containsOnlyKeys("size");
        assertThat(errorsOf(() -> parse("one", "ten", null, null, null, null, null, null, null)))
                .containsOnlyKeys("page", "size");
        assertThat(errorsOf(() -> parse("99999999999", null, null, null, null, null, null, null, null)))
                .containsOnlyKeys("page");
    }

    @Test
    void acceptsEverySupportedSortFieldIgnoringCase() {
        assertThat(parse(null, null, "id", null, null, null, null, null, null).sortBy())
                .isEqualTo(TicketSortField.ID);
        assertThat(parse(null, null, "createdAt", null, null, null, null, null, null).sortBy())
                .isEqualTo(TicketSortField.CREATED_AT);
        assertThat(parse(null, null, "UPDATEDAT", null, null, null, null, null, null).sortBy())
                .isEqualTo(TicketSortField.UPDATED_AT);
        assertThat(parse(null, null, "priority", null, null, null, null, null, null).sortBy())
                .isEqualTo(TicketSortField.PRIORITY);
        assertThat(parse(null, null, "Status", null, null, null, null, null, null).sortBy())
                .isEqualTo(TicketSortField.STATUS);
    }

    @Test
    void rejectsUnsupportedSortFieldsAndListsTheSupportedOnes() {
        for (String bad : List.of("title", "description", "version", "priorityRank", "id; drop table tickets")) {
            Map<String, String> errors = errorsOf(() -> parse(null, null, bad, null, null, null, null, null, null));
            assertThat(errors).containsOnlyKeys("sortBy");
            assertThat(errors.get("sortBy")).contains("id", "createdAt", "updatedAt", "priority", "status");
        }
    }

    @Test
    void parsesDirectionIgnoringCaseAndRejectsOthers() {
        assertThat(parse(null, null, null, "asc", null, null, null, null, null).direction())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(parse(null, null, null, "DESC", null, null, null, null, null).direction())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(errorsOf(() -> parse(null, null, null, "sideways", null, null, null, null, null)))
                .containsOnlyKeys("direction");
    }

    @Test
    void sortsByTheChosenFieldWithIdAsTieBreaker() {
        Sort sort = parse(null, null, "priority", "asc", null, null, null, null, null).toPageable().getSort();

        assertThat(sort.stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("priorityRank:ASC", "id:ASC");
    }

    @Test
    void sortingByIdDoesNotAddADuplicateTieBreaker() {
        Sort sort = parse(null, null, "id", "desc", null, null, null, null, null).toPageable().getSort();

        assertThat(sort.stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("id:DESC");
    }

    @Test
    void mapsStatusAndPrioritySortToTheRankProperties() {
        assertThat(TicketSortField.PRIORITY.property()).isEqualTo("priorityRank");
        assertThat(TicketSortField.STATUS.property()).isEqualTo("statusRank");
    }

    @Test
    void parsesSeveralStatusesAndPrioritiesIgnoringCase() {
        TicketListQuery query = parse(null, null, null, null,
                List.of("open", "IN_PROGRESS", "open"), List.of("high", "Low"), null, null, null);

        assertThat(query.statuses()).containsExactlyInAnyOrder(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        assertThat(query.priorities()).containsExactlyInAnyOrder(TicketPriority.HIGH, TicketPriority.LOW);
    }

    @Test
    void rejectsUnknownStatusAndPriorityValues() {
        Map<String, String> errors = errorsOf(() -> parse(null, null, null, null,
                List.of("OPEN", "DONE"), List.of("URGENT"), null, null, null));

        assertThat(errors).containsOnlyKeys("status", "priority");
        assertThat(errors.get("status")).contains("DONE").contains("OPEN").contains("CANCELLED");
        assertThat(errors.get("priority")).contains("URGENT").contains("LOW").contains("HIGH");
    }

    @Test
    void createdDateRangeIsInclusiveInUtc() {
        TicketListQuery query = parse(null, null, null, null, null, null, "2026-11-01", "2026-11-30", null);

        assertThat(query.createdFrom()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        // Exclusive bound = start of the day after createdTo, so all of 30 November is included.
        assertThat(query.createdBefore()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void acceptsAnOpenEndedDateRange() {
        TicketListQuery fromOnly = parse(null, null, null, null, null, null, "2026-11-01", null, null);
        TicketListQuery toOnly = parse(null, null, null, null, null, null, null, "2026-11-01", null);

        assertThat(fromOnly.createdFrom()).isNotNull();
        assertThat(fromOnly.createdBefore()).isNull();
        assertThat(toOnly.createdFrom()).isNull();
        assertThat(toOnly.createdBefore()).isNotNull();
    }

    @Test
    void acceptsASingleDayRange() {
        assertThat(parse(null, null, null, null, null, null, "2026-11-05", "2026-11-05", null).createdBefore())
                .isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
    }

    @Test
    void rejectsMalformedAndImpossibleDatesAndReversedRanges() {
        assertThat(errorsOf(() -> parse(null, null, null, null, null, null, "01/11/2026", null, null)))
                .containsOnlyKeys("createdFrom");
        assertThat(errorsOf(() -> parse(null, null, null, null, null, null, null, "2026-02-30", null)))
                .containsOnlyKeys("createdTo");
        assertThat(errorsOf(() -> parse(null, null, null, null, null, null, "2026-11-30", "2026-11-01", null)))
                .containsOnlyKeys("createdTo");
    }

    @Test
    void trimsSearchTextAndRejectsOverlongText() {
        assertThat(parse(null, null, null, null, null, null, null, null, "  VPN  ").search()).isEqualTo("VPN");
        assertThat(errorsOf(() -> parse(null, null, null, null, null, null, null, null, "x".repeat(101))))
                .containsOnlyKeys("q");
        assertThat(parse(null, null, null, null, null, null, null, null, "x".repeat(100)).search()).hasSize(100);
    }

    @Test
    void reportsAllProblemsAtOnce() {
        Map<String, String> errors = errorsOf(() -> parse("x", "0", "nope", "up",
                List.of("NOPE"), List.of("NOPE"), "bad", "bad", null));

        assertThat(errors).containsOnlyKeys(
                "page", "size", "sortBy", "direction", "status", "priority", "createdFrom", "createdTo");
    }

    @Test
    void invalidQueryExceptionCarriesAGenericMessage() {
        assertThatThrownBy(() -> parse("-1", null, null, null, null, null, null, null, null))
                .isInstanceOf(InvalidTicketQueryException.class)
                .hasMessage("Invalid query parameters");
    }
}
