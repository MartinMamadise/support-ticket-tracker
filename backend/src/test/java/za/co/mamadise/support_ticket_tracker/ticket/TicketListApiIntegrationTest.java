package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import za.co.mamadise.support_ticket_tracker.TestDatabase;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Pagination, sorting, filtering and search of GET /api/tickets against the real service,
 * repository and in-memory H2 database (so the SQL generated for each feature is exercised).
 */
@WithUserDetails(value = TestDatabase.ADMIN, setupBefore = TestExecutionEvent.TEST_EXECUTION)
@Import(TestDatabase.class)
@SpringBootTest
@AutoConfigureMockMvc
class TicketListApiIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-11-01T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    /** Ticket number of the next seeded ticket; also used to give each one a distinct creation time. */
    private int sequence;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void cleanDatabase() {
        database.reset();
        database.admin();
        sequence = 0;
    }

    /** Saves a ticket directly, created {@code sequence} hours after BASE (so later tickets are newer). */
    private Ticket seed(String title, String description, TicketStatus status, TicketPriority priority) {
        return seedAt(title, description, status, priority, BASE.plusSeconds(3600L * sequence++));
    }

    private Ticket seedAt(String title, String description, TicketStatus status, TicketPriority priority,
            Instant createdAt) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        // Set directly: these tests exercise querying, not the workflow (covered by TicketTest).
        ReflectionTestUtils.setField(ticket, "status", status);
        if (status.isResolution()) {
            ReflectionTestUtils.setField(ticket, "resolvedAt", createdAt.plusSeconds(60));
        }
        ReflectionTestUtils.setField(ticket, "createdAt", createdAt);
        ReflectionTestUtils.setField(ticket, "updatedAt", createdAt);
        return ticketRepository.saveAndFlush(ticket);
    }

    private Ticket seed(String title) {
        return seed(title, "Details for " + title, TicketStatus.OPEN, TicketPriority.MEDIUM);
    }

    private void seedMany(int count) {
        for (int i = 1; i <= count; i++) {
            seed("Ticket " + i);
        }
    }

    private ResultActions list(String... paramsAsNameValuePairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/tickets");
        for (int i = 0; i < paramsAsNameValuePairs.length; i += 2) {
            request.param(paramsAsNameValuePairs[i], paramsAsNameValuePairs[i + 1]);
        }
        return mockMvc.perform(request);
    }

    // ---------------------------------------------------------------- pagination

    /** All Spring tests share one H2 database: leave it empty for the next class. */
    @AfterEach
    void resetAfter() {
        database.reset();
    }

    @Test
    void defaultPaginationReturnsFirstTwentyNewestFirstWithMetadata() throws Exception {
        seedMany(25);

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.content[0].title").value("Ticket 25"))
                .andExpect(jsonPath("$.content[19].title").value("Ticket 6"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void customPageSizeAndPageNumber() throws Exception {
        seedMany(25);

        list("size", "10", "page", "2", "sortBy", "id", "direction", "asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].title").value("Ticket 21"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));
    }

    @Test
    void middlePageHasBothNeighbours() throws Exception {
        seedMany(25);

        list("size", "10", "page", "1")
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(true));
    }

    @Test
    void pageSizeAboveTheMaximumIsReducedToOneHundred() throws Exception {
        seedMany(101);

        list("size", "1000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(100)))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void emptyDatabaseGivesAnEmptyFirstPage() throws Exception {
        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void pageBeyondTheLastIsEmptyButStillReportsTheTotals() throws Exception {
        seedMany(3);

        list("page", "5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));
    }

    @Test
    void pagesDoNotOverlapOrSkipTicketsEvenWhenSortValuesAreEqual() throws Exception {
        for (int i = 0; i < 7; i++) {
            seed("Same priority " + i, "D", TicketStatus.OPEN, TicketPriority.HIGH);
        }

        Set<Integer> seen = new HashSet<>();
        for (int page = 0; page < 4; page++) {
            String json = list("sortBy", "priority", "size", "2", "page", String.valueOf(page))
                    .andReturn().getResponse().getContentAsString();
            List<Integer> ids = JsonPath.read(json, "$.content[*].id");
            for (Integer id : ids) {
                // add() is false if the same ticket appeared on two pages
                assertThat(seen.add(id)).as("ticket %s repeated", id).isTrue();
            }
        }
        assertThat(seen).hasSize(7);
    }

    // ---------------------------------------------------------------- sorting

    @Test
    void sortsByCreatedDateBothWays() throws Exception {
        seed("First");
        seed("Second");
        seed("Third");

        list("sortBy", "createdAt", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("First", "Second", "Third")));
        list("sortBy", "createdAt", "direction", "desc")
                .andExpect(jsonPath("$.content[*].title", contains("Third", "Second", "First")));
    }

    @Test
    void sortsByUpdatedDate() throws Exception {
        Ticket a = seed("A");
        seed("B");
        // Any update stamps the current time (@PreUpdate), so A becomes the most recently updated ticket.
        a.setTitle("A edited");
        ticketRepository.saveAndFlush(a);

        list("sortBy", "updatedAt", "direction", "desc")
                .andExpect(jsonPath("$.content[0].title").value("A edited"))
                .andExpect(jsonPath("$.content[1].title").value("B"));
        list("sortBy", "updatedAt", "direction", "asc")
                .andExpect(jsonPath("$.content[0].title").value("B"))
                .andExpect(jsonPath("$.content[1].title").value("A edited"));
    }

    @Test
    void sortsByTicketId() throws Exception {
        seed("One");
        seed("Two");
        seed("Three");

        list("sortBy", "id", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("One", "Two", "Three")));
        list("sortBy", "id", "direction", "desc")
                .andExpect(jsonPath("$.content[*].title", contains("Three", "Two", "One")));
    }

    @Test
    void sortsByPriorityByMeaningNotAlphabetically() throws Exception {
        seed("Medium one", "D", TicketStatus.OPEN, TicketPriority.MEDIUM);
        seed("High one", "D", TicketStatus.OPEN, TicketPriority.HIGH);
        seed("Low one", "D", TicketStatus.OPEN, TicketPriority.LOW);

        list("sortBy", "priority", "direction", "asc")
                .andExpect(jsonPath("$.content[*].priority", contains("LOW", "MEDIUM", "HIGH")));
        list("sortBy", "priority", "direction", "desc")
                .andExpect(jsonPath("$.content[*].priority", contains("HIGH", "MEDIUM", "LOW")));
    }

    @Test
    void sortsByStatusInWorkflowOrder() throws Exception {
        seed("Cancelled", "D", TicketStatus.CANCELLED, TicketPriority.LOW);
        seed("Resolved", "D", TicketStatus.RESOLVED, TicketPriority.LOW);
        seed("Open", "D", TicketStatus.OPEN, TicketPriority.LOW);
        seed("Closed", "D", TicketStatus.CLOSED, TicketPriority.LOW);
        seed("In progress", "D", TicketStatus.IN_PROGRESS, TicketPriority.LOW);

        list("sortBy", "status", "direction", "asc")
                .andExpect(jsonPath("$.content[*].status",
                        contains("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED")));
        list("sortBy", "status", "direction", "desc")
                .andExpect(jsonPath("$.content[*].status",
                        contains("CANCELLED", "CLOSED", "RESOLVED", "IN_PROGRESS", "OPEN")));
    }

    @Test
    void sortingAppliesAcrossPagesNotJustWithinOne() throws Exception {
        seed("Low", "D", TicketStatus.OPEN, TicketPriority.LOW);
        seed("High", "D", TicketStatus.OPEN, TicketPriority.HIGH);
        seed("Medium", "D", TicketStatus.OPEN, TicketPriority.MEDIUM);

        list("sortBy", "priority", "direction", "desc", "size", "1", "page", "0")
                .andExpect(jsonPath("$.content[0].title").value("High"));
        list("sortBy", "priority", "direction", "desc", "size", "1", "page", "2")
                .andExpect(jsonPath("$.content[0].title").value("Low"));
    }

    @Test
    void rejectsUnsupportedSortFieldsAndDirections() throws Exception {
        seedMany(1);

        list("sortBy", "title").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid query parameters"))
                .andExpect(jsonPath("$.fieldErrors.sortBy", containsString("createdAt")));
        list("sortBy", "version").andExpect(status().isBadRequest());
        list("sortBy", "priorityRank").andExpect(status().isBadRequest());
        list("direction", "sideways").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.direction").exists());
    }

    // ---------------------------------------------------------------- filtering

    private void seedMixed() {
        seed("Open low", "D", TicketStatus.OPEN, TicketPriority.LOW);
        seed("Open high", "D", TicketStatus.OPEN, TicketPriority.HIGH);
        seed("Progress high", "D", TicketStatus.IN_PROGRESS, TicketPriority.HIGH);
        seed("Resolved medium", "D", TicketStatus.RESOLVED, TicketPriority.MEDIUM);
        seed("Closed low", "D", TicketStatus.CLOSED, TicketPriority.LOW);
    }

    @Test
    void filtersByStatus() throws Exception {
        seedMixed();

        list("status", "OPEN")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Open low", "Open high")));
    }

    @Test
    void filtersByStatusIgnoringCaseAndByMoreThanOneStatus() throws Exception {
        seedMixed();

        list("status", "closed").andExpect(jsonPath("$.content[*].title", contains("Closed low")));
        list("status", "OPEN,RESOLVED").andExpect(jsonPath("$.totalElements").value(3));
        list("status", "OPEN", "status", "RESOLVED").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtersByPriority() throws Exception {
        seedMixed();

        list("priority", "HIGH")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Open high", "Progress high")));
        list("priority", "LOW,MEDIUM").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void combinesStatusAndPriorityFilters() throws Exception {
        seedMixed();

        list("status", "OPEN", "priority", "HIGH")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Open high"));
        list("status", "CLOSED", "priority", "HIGH")
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByCreatedDateRangeInclusiveOfBothEndDays() throws Exception {
        seedAt("Oct 31 late", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-10-31T23:59:59Z"));
        seedAt("Nov 1 early", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-11-01T00:00:00Z"));
        seedAt("Nov 15", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-11-15T12:00:00Z"));
        seedAt("Nov 30 late", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-11-30T23:59:59Z"));
        seedAt("Dec 1 early", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-12-01T00:00:00Z"));

        list("createdFrom", "2026-11-01", "createdTo", "2026-11-30", "sortBy", "createdAt", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("Nov 1 early", "Nov 15", "Nov 30 late")));
        list("createdFrom", "2026-11-15", "sortBy", "createdAt", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("Nov 15", "Nov 30 late", "Dec 1 early")));
        list("createdTo", "2026-11-01", "sortBy", "createdAt", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("Oct 31 late", "Nov 1 early")));
        list("createdFrom", "2026-11-15", "createdTo", "2026-11-15")
                .andExpect(jsonPath("$.content[*].title", contains("Nov 15")));
    }

    @Test
    void combinesDateRangeWithStatusAndPriority() throws Exception {
        seedAt("Match", "D", TicketStatus.OPEN, TicketPriority.HIGH, Instant.parse("2026-11-10T10:00:00Z"));
        seedAt("Wrong status", "D", TicketStatus.CLOSED, TicketPriority.HIGH, Instant.parse("2026-11-10T10:00:00Z"));
        seedAt("Wrong priority", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-11-10T10:00:00Z"));
        seedAt("Wrong date", "D", TicketStatus.OPEN, TicketPriority.HIGH, Instant.parse("2026-10-10T10:00:00Z"));

        list("status", "OPEN", "priority", "HIGH", "createdFrom", "2026-11-01", "createdTo", "2026-11-30")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Match"));
    }

    @Test
    void filtersAreOptionalAndPaginationTotalsReflectTheFilter() throws Exception {
        for (int i = 0; i < 12; i++) {
            seed("Open " + i, "D", TicketStatus.OPEN, TicketPriority.LOW);
        }
        seed("Closed", "D", TicketStatus.CLOSED, TicketPriority.LOW);

        list().andExpect(jsonPath("$.totalElements").value(13));
        list("status", "OPEN", "size", "5", "page", "2")
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    // ---------------------------------------------------------------- search

    @Test
    void searchesTitleCaseInsensitively() throws Exception {
        seed("VPN connection drops");
        seed("Printer jam");

        list("q", "vpn").andExpect(jsonPath("$.content[*].title", contains("VPN connection drops")));
        list("q", "PRINTER").andExpect(jsonPath("$.content[*].title", contains("Printer jam")));
    }

    @Test
    void searchesDescription() throws Exception {
        seed("Laptop", "Battery drains within an hour", TicketStatus.OPEN, TicketPriority.LOW);
        seed("Monitor", "Screen flickers", TicketStatus.OPEN, TicketPriority.LOW);

        list("q", "BATTERY").andExpect(jsonPath("$.content[*].title", contains("Laptop")));
    }

    @Test
    void searchesByTicketNumberWithOrWithoutHash() throws Exception {
        Ticket target = seed("Unrelated title");
        seed("Another one");

        list("q", String.valueOf(target.getId()))
                .andExpect(jsonPath("$.content[*].id", contains(target.getId().intValue())));
        list("q", "#" + target.getId())
                .andExpect(jsonPath("$.content[*].id", contains(target.getId().intValue())));
    }

    @Test
    void numericSearchAlsoMatchesTextContainingTheNumber() throws Exception {
        seed("Error 500 on login");
        seed("Printer jam");

        list("q", "500").andExpect(jsonPath("$.content[*].title", contains("Error 500 on login")));
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsPlainCharacters() throws Exception {
        seed("Disk 100% full");
        seed("Disk 100 pieces");
        seed("snake_case naming");
        seed("snakeXcase naming");

        list("q", "100%").andExpect(jsonPath("$.content[*].title", contains("Disk 100% full")));
        list("q", "snake_case").andExpect(jsonPath("$.content[*].title", contains("snake_case naming")));
        list("q", "%").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchWithNoMatchGivesAnEmptyPage() throws Exception {
        seed("Printer jam");

        list("q", "zebra").andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void blankSearchIsIgnored() throws Exception {
        seedMany(3);

        list("q", "   ").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void searchCombinesWithStatusAndPriorityFilters() throws Exception {
        seed("Printer jam", "D", TicketStatus.OPEN, TicketPriority.HIGH);
        seed("Printer toner", "D", TicketStatus.CLOSED, TicketPriority.HIGH);
        seed("Printer paper", "D", TicketStatus.OPEN, TicketPriority.LOW);
        seed("Laptop", "D", TicketStatus.OPEN, TicketPriority.HIGH);

        list("q", "printer").andExpect(jsonPath("$.totalElements").value(3));
        list("q", "printer", "status", "OPEN")
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Printer jam", "Printer paper")));
        list("q", "printer", "status", "OPEN", "priority", "HIGH")
                .andExpect(jsonPath("$.content[*].title", contains("Printer jam")));
    }

    @Test
    void searchCombinesWithDateRange() throws Exception {
        seedAt("Printer old", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-09-01T10:00:00Z"));
        seedAt("Printer new", "D", TicketStatus.OPEN, TicketPriority.LOW, Instant.parse("2026-11-10T10:00:00Z"));

        list("q", "printer", "createdFrom", "2026-11-01")
                .andExpect(jsonPath("$.content[*].title", contains("Printer new")));
    }

    @Test
    void searchCombinesWithPaginationAndSorting() throws Exception {
        for (int i = 1; i <= 5; i++) {
            seed("Printer " + i);
        }
        seed("Laptop");

        list("q", "printer", "size", "2", "page", "1", "sortBy", "id", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("Printer 3", "Printer 4")))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(true));
        list("q", "printer", "size", "2", "page", "2", "sortBy", "id", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("Printer 5")))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ---------------------------------------------------------------- invalid parameters

    @Test
    void rejectsInvalidPagingParameters() throws Exception {
        list("page", "-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").exists());
        list("page", "abc").andExpect(status().isBadRequest());
        list("size", "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
        list("size", "-3").andExpect(status().isBadRequest());
        list("size", "many").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidFilterValues() throws Exception {
        list("status", "DONE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status", containsString("DONE")));
        list("priority", "URGENT").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priority", containsString("URGENT")));
        list("status", "OPEN,DONE").andExpect(status().isBadRequest());
        list("createdFrom", "yesterday").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.createdFrom").exists());
        list("createdTo", "2026-13-01").andExpect(status().isBadRequest());
        list("createdFrom", "2026-11-30", "createdTo", "2026-11-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.createdTo").exists());
    }

    @Test
    void reportsEveryInvalidParameterInOneResponse() throws Exception {
        list("page", "x", "sortBy", "nope", "status", "DONE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid query parameters"))
                .andExpect(jsonPath("$.fieldErrors.page").exists())
                .andExpect(jsonPath("$.fieldErrors.sortBy").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void invalidRequestsDoNotReturnAnyTickets() throws Exception {
        seedMany(2);

        list("sortBy", "nope").andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void responseTicketsAreDtosWithoutInternalSortColumns() throws Exception {
        seed("Printer jam");

        list().andExpect(jsonPath("$.content[0].priorityRank").doesNotExist())
                .andExpect(jsonPath("$.content[0].statusRank").doesNotExist())
                .andExpect(jsonPath("$.content[0].allowedTransitions").exists());
    }
}
