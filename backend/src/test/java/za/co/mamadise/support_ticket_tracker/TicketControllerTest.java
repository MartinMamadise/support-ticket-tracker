package za.co.mamadise.support_ticket_tracker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the HTTP layer (status codes, validation, error format) with the service mocked. */
@WebMvcTest(TicketController.class)
class TicketControllerTest {

    private static final String VALID_BODY = """
            {"title":"VPN down","description":"Cannot connect","status":"OPEN","priority":"HIGH"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    private Ticket ticket(long id) {
        Ticket t = new Ticket();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle("VPN down");
        t.setDescription("Cannot connect");
        t.setStatus("OPEN");
        t.setPriority("HIGH");
        return t;
    }

    @Test
    void getAllReturnsTickets() throws Exception {
        when(ticketService.getAllTickets()).thenReturn(List.of(ticket(1), ticket(2)));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("VPN down"));
    }

    @Test
    void getByIdReturnsTicket() throws Exception {
        when(ticketService.getTicketById(1L)).thenReturn(ticket(1));

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.priority").value("HIGH"));
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

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tickets/7"))
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        String body = """
                {"title":"  ","description":"Cannot connect","priority":"HIGH"}""";

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
    }

    @Test
    void createRejectsInvalidStatusAndPriority() throws Exception {
        String body = """
                {"title":"T","description":"D","status":"DONE","priority":"URGENT"}""";

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists())
                .andExpect(jsonPath("$.fieldErrors.priority").exists());
    }

    @Test
    void createRejectsMissingPriority() throws Exception {
        String body = """
                {"title":"T","description":"D"}""";

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priority").value("Priority is required"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateReturnsUpdatedTicket() throws Exception {
        when(ticketService.updateTicket(eq(1L), any(TicketRequest.class))).thenReturn(ticket(1));

        mockMvc.perform(put("/api/tickets/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        when(ticketService.updateTicket(eq(99L), any(TicketRequest.class)))
                .thenThrow(new TicketNotFoundException(99L));

        mockMvc.perform(put("/api/tickets/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidBody() throws Exception {
        String body = """
                {"title":"","description":"D","priority":"LOW"}""";

        mockMvc.perform(put("/api/tickets/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/tickets/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        doThrow(new TicketNotFoundException(99L)).when(ticketService).deleteTicket(99L);

        mockMvc.perform(delete("/api/tickets/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethodReturns405NotServerError() throws Exception {
        mockMvc.perform(put("/api/tickets"))
                .andExpect(status().isMethodNotAllowed());
    }
}
