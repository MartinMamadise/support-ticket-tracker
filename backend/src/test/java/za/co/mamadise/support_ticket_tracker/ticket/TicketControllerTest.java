package za.co.mamadise.support_ticket_tracker.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the HTTP layer (status codes, validation, error format) with the service mocked. */
@WithMockUser(roles = "ADMIN")
@WebMvcTest(TicketController.class)
class TicketControllerTest {

    private static final String VALID_BODY = """
            {"title":"VPN down","description":"Cannot connect","status":"OPEN","priority":"HIGH"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    private TicketResponse ticket(long id) {
        Instant created = Instant.parse("2026-11-21T10:15:30Z");
        return new TicketResponse(id, "VPN down", "Cannot connect", TicketStatus.OPEN,
                TicketPriority.HIGH, TicketCategory.OTHER, null, null, null, created, created, null, 0L,
                TicketStatus.OPEN.allowedTransitionList(), null, null);
    }

    @Test
    void listReturnsPageWithMetadata() throws Exception {
        when(ticketService.listTickets(any(TicketListQuery.class))).thenReturn(
                new PageResponse<>(List.of(ticket(1), ticket(2)), 0, 20, 2L, 1, false, false));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("VPN down"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void listWithoutParametersUsesDefaults() throws Exception {
        when(ticketService.listTickets(any(TicketListQuery.class))).thenReturn(
                new PageResponse<>(List.of(), 0, 20, 0L, 0, false, false));

        mockMvc.perform(get("/api/tickets")).andExpect(status().isOk());

        ArgumentCaptor<TicketListQuery> captor = ArgumentCaptor.forClass(TicketListQuery.class);
        verify(ticketService).listTickets(captor.capture());
        TicketListQuery query = captor.getValue();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(TicketListQuery.DEFAULT_SIZE);
        assertThat(query.sortBy()).isEqualTo(TicketSortField.CREATED_AT);
        assertThat(query.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(query.statuses()).isEmpty();
        assertThat(query.priorities()).isEmpty();
        assertThat(query.search()).isNull();
    }

    @Test
    void listPassesAllQueryParametersToTheService() throws Exception {
        when(ticketService.listTickets(any(TicketListQuery.class))).thenReturn(
                new PageResponse<>(List.of(), 2, 5, 0L, 0, false, true));

        mockMvc.perform(get("/api/tickets")
                        .param("page", "2").param("size", "5")
                        .param("sortBy", "priority").param("direction", "asc")
                        .param("status", "OPEN", "IN_PROGRESS").param("priority", "HIGH")
                        .param("createdFrom", "2026-11-01").param("createdTo", "2026-11-30")
                        .param("q", "  vpn "))
                .andExpect(status().isOk());

        ArgumentCaptor<TicketListQuery> captor = ArgumentCaptor.forClass(TicketListQuery.class);
        verify(ticketService).listTickets(captor.capture());
        TicketListQuery query = captor.getValue();
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(5);
        assertThat(query.sortBy()).isEqualTo(TicketSortField.PRIORITY);
        assertThat(query.direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(query.statuses()).containsExactlyInAnyOrder(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        assertThat(query.priorities()).containsExactly(TicketPriority.HIGH);
        assertThat(query.createdFrom()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(query.createdBefore()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(query.search()).isEqualTo("vpn");
    }

    @Test
    void listRejectsInvalidParametersWith400AndFieldErrorsWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .param("page", "-1").param("sortBy", "password").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid query parameters"))
                .andExpect(jsonPath("$.fieldErrors.page").exists())
                .andExpect(jsonPath("$.fieldErrors.sortBy").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        verifyNoInteractions(ticketService);
    }

    @Test
    void getByIdReturnsTicket() throws Exception {
        when(ticketService.getTicketById(1L)).thenReturn(ticket(1));

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-21T10:15:30Z"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.allowedTransitions[1]").value("CANCELLED"));
    }

    @Test
    void getByIdReturns404WithErrorBodyWhenMissing() throws Exception {
        when(ticketService.getTicketById(99L)).thenThrow(new TicketNotFoundException(99L));

        mockMvc.perform(get("/api/tickets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Ticket not found with ID: 99"));
    }

    @Test
    void getByIdReturns400ForNonNumericId() throws Exception {
        mockMvc.perform(get("/api/tickets/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(ticketService.createTicket(any(TicketRequest.class))).thenReturn(ticket(7));

        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tickets/7"))
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        String body = """
                {"title":"  ","description":"Cannot connect","priority":"HIGH"}""";

        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
    }

    @Test
    void createRejectsInvalidStatusAndPriority() throws Exception {
        String body = """
                {"title":"T","description":"D","status":"DONE","priority":"URGENT"}""";

        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists())
                .andExpect(jsonPath("$.fieldErrors.priority").exists());
    }

    @Test
    void updateRejectsNegativeVersion() throws Exception {
        String body = """
                {"title":"T","description":"D","priority":"LOW","version":-1}""";

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());
    }

    @Test
    void updateReturns409WithMessageWhenTransitionIsForbidden() throws Exception {
        when(ticketService.updateTicket(eq(1L), any(TicketRequest.class)))
                .thenThrow(TicketWorkflowException.invalidTransition(TicketStatus.OPEN, TicketStatus.RESOLVED));

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Cannot change ticket status from OPEN to RESOLVED. Allowed: [IN_PROGRESS, CANCELLED]."));
    }

    @Test
    void createReturns409WhenInitialStatusIsNotOpen() throws Exception {
        when(ticketService.createTicket(any(TicketRequest.class)))
                .thenThrow(TicketWorkflowException.invalidInitialStatus(TicketStatus.RESOLVED));

        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelledIsAcceptedAsAStatusValue() throws Exception {
        when(ticketService.updateTicket(eq(1L), any(TicketRequest.class))).thenReturn(ticket(1));

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"T","description":"D","status":"CANCELLED","priority":"LOW"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void updateRejectsOverlongDescription() throws Exception {
        String body = "{\"title\":\"T\",\"description\":\"" + "x".repeat(2001) + "\",\"priority\":\"LOW\"}";

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void updateReturns409WhenTicketWasChangedBySomeoneElse() throws Exception {
        when(ticketService.updateTicket(eq(1L), any(TicketRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Ticket.class, 1L));

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createRejectsMissingPriority() throws Exception {
        String body = """
                {"title":"T","description":"D"}""";

        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priority").value("Priority is required"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateReturnsUpdatedTicket() throws Exception {
        when(ticketService.updateTicket(eq(1L), any(TicketRequest.class))).thenReturn(ticket(1));

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        when(ticketService.updateTicket(eq(99L), any(TicketRequest.class)))
                .thenThrow(new TicketNotFoundException(99L));

        mockMvc.perform(put("/api/tickets/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidBody() throws Exception {
        String body = """
                {"title":"","description":"D","priority":"LOW"}""";

        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/tickets/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        doThrow(new TicketNotFoundException(99L)).when(ticketService).deleteTicket(99L);

        mockMvc.perform(delete("/api/tickets/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethodReturns405NotServerError() throws Exception {
        mockMvc.perform(put("/api/tickets").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}
