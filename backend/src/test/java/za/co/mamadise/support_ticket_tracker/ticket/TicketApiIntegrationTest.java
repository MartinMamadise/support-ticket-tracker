package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import za.co.mamadise.support_ticket_tracker.TestDatabase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end API tests: real controller, service, repository and (in-memory) database. */
// A real ADMIN account (deleting tickets is administrator-only; admins see every ticket, including seeded ones).
@WithUserDetails(value = TestDatabase.ADMIN, setupBefore = TestExecutionEvent.TEST_EXECUTION)
@Import(TestDatabase.class)
@SpringBootTest
@AutoConfigureMockMvc
class TicketApiIntegrationTest {

    /** ISO-8601 instant in UTC, e.g. 2026-11-21T12:38:00.123456Z. */
    private static final String UTC_INSTANT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void cleanDatabase() {
        database.reset();
        database.admin();
    }

    private long createTicket(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    /** All Spring tests share one H2 database: leave it empty for the next class. */
    @AfterEach
    void resetAfter() {
        database.reset();
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        long id = createTicket("""
                {"title":"Email not syncing","description":"Outlook stuck","priority":"MEDIUM"}""");

        // Created with default status and timestamps
        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt", matchesPattern(UTC_INSTANT)))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));

        // Appears in the list
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Update (start work, then resolve)
        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Email not syncing","description":"Looking into it","status":"IN_PROGRESS","priority":"LOW"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Email not syncing","description":"Fixed profile","status":"RESOLVED","priority":"LOW"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.priority").value("LOW"));

        // Delete, then it is gone
        mockMvc.perform(delete("/api/tickets/" + id).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tickets/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void clientCannotOverwriteExistingTicketByPostingAnId() throws Exception {
        long first = createTicket("""
                {"title":"First","description":"Original","priority":"LOW"}""");

        long second = createTicket("{\"id\":" + first
                + ",\"title\":\"Second\",\"description\":\"New\",\"priority\":\"HIGH\"}");

        mockMvc.perform(get("/api/tickets/" + first))
                .andExpect(jsonPath("$.title").value("First"));
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void invalidCreateIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"\",\"priority\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.priority").exists());

        mockMvc.perform(get("/api/tickets")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void missingTicketReturns404ForGetPutAndDelete() throws Exception {
        String body = "{\"title\":\"T\",\"description\":\"D\",\"priority\":\"LOW\"}";

        mockMvc.perform(get("/api/tickets/9999")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tickets/9999").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tickets/9999").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void titleLongerThan150CharactersIsRejected() throws Exception {
        String longTitle = "x".repeat(151);

        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"description\":\"D\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void responseExposesOnlyDtoFields() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    private ResultActions changeStatus(long id, String status) throws Exception {
        return mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"description\":\"D\",\"status\":\"" + status
                        + "\",\"priority\":\"LOW\"}"));
    }

    @Test
    void resolvingRecordsResolutionTimeAndReopeningClearsIt() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        changeStatus(id, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        changeStatus(id, "RESOLVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt", matchesPattern(UTC_INSTANT)))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("OPEN"));

        // Persisted, not just echoed.
        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.resolvedAt", matchesPattern(UTC_INSTANT)));

        changeStatus(id, "OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    @Test
    void closingKeepsTheResolutionTimeAndMakesTheTicketFinal() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");
        changeStatus(id, "IN_PROGRESS").andExpect(status().isOk());
        changeStatus(id, "RESOLVED").andExpect(status().isOk());
        Instant resolvedAt = ticketRepository.findById(id).orElseThrow().getResolvedAt();
        assertThat(resolvedAt).isNotNull();

        changeStatus(id, "CLOSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(0));
        assertThat(ticketRepository.findById(id).orElseThrow().getResolvedAt()).isEqualTo(resolvedAt);

        // Final: neither a reopen nor a plain edit is accepted.
        changeStatus(id, "OPEN").andExpect(status().isConflict());
        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Edited","description":"D","priority":"LOW"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ticket is CLOSED and can no longer be edited."));
    }

    @Test
    void cancellingFromOpenWorksAndHasNoResolutionTime() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        changeStatus(id, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    @Test
    void invalidTransitionReturns409WithMessageAndChangesNothing() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Sneaky edit","description":"D","status":"RESOLVED","priority":"HIGH"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Cannot change ticket status from OPEN to RESOLVED. Allowed: [IN_PROGRESS, CANCELLED]."));

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.title").value("T"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    @Test
    void creatingATicketThatIsAlreadyResolvedIsRejected() throws Exception {
        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"T","description":"D","status":"RESOLVED","priority":"LOW"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("New tickets must start as OPEN, but status RESOLVED was requested."));

        mockMvc.perform(get("/api/tickets")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void whitespaceOnlyTitleAndDescriptionAreRejectedOnUpdate() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"   ","description":"  ","priority":"LOW"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void statusChangeOnMissingTicketReturns404() throws Exception {
        changeStatus(424242L, "IN_PROGRESS").andExpect(status().isNotFound());
    }

    @Test
    void updateIncrementsVersionAndRejectsStaleVersionWith409() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        // Version 0 is current: accepted, version becomes 1.
        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"First edit","description":"D","priority":"LOW","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // A second client still holding version 0 is rejected and changes nothing.
        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Second edit","description":"D","priority":"LOW","version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.title").value("First edit"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateWithoutVersionStillWorks() throws Exception {
        long id = createTicket("""
                {"title":"T","description":"D","priority":"LOW"}""");

        mockMvc.perform(put("/api/tickets/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Edited","description":"D","priority":"HIGH"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited"));
    }

    @Test
    void databaseRejectsConcurrentWriteFromStaleEntity() {
        Ticket created = new Ticket();
        created.setTitle("Race");
        created.setDescription("D");
        created.setPriority(TicketPriority.LOW);
        Long id = ticketRepository.saveAndFlush(created).getId();

        // Two independent loads (no surrounding transaction) give two detached copies.
        Ticket first = ticketRepository.findById(id).orElseThrow();
        Ticket second = ticketRepository.findById(id).orElseThrow();

        first.setTitle("Winner");
        ticketRepository.saveAndFlush(first);

        second.setTitle("Loser");
        assertThatThrownBy(() -> ticketRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void invalidStatusIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"description\":\"D\",\"status\":\"open\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }
}
