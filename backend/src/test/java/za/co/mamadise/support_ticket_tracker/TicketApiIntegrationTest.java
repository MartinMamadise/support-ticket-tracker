package za.co.mamadise.support_ticket_tracker;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end API tests: real controller, service, repository and (in-memory) database. */
@SpringBootTest
@AutoConfigureMockMvc
class TicketApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void cleanDatabase() {
        ticketRepository.deleteAll();
    }

    private long createTicket(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        long id = createTicket("""
                {"title":"Email not syncing","description":"Outlook stuck","priority":"MEDIUM"}""");

        // Created with default status and timestamps
        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));

        // Appears in the list
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Update
        mockMvc.perform(put("/api/tickets/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Email not syncing","description":"Fixed profile","status":"RESOLVED","priority":"LOW"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.priority").value("LOW"));

        // Delete, then it is gone
        mockMvc.perform(delete("/api/tickets/" + id)).andExpect(status().isNoContent());
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
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
    }

    @Test
    void invalidCreateIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"\",\"priority\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.priority").exists());

        mockMvc.perform(get("/api/tickets")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void missingTicketReturns404ForGetPutAndDelete() throws Exception {
        String body = "{\"title\":\"T\",\"description\":\"D\",\"priority\":\"LOW\"}";

        mockMvc.perform(get("/api/tickets/9999")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tickets/9999").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tickets/9999")).andExpect(status().isNotFound());
    }

    @Test
    void titleLongerThan150CharactersIsRejected() throws Exception {
        String longTitle = "x".repeat(151);

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"description\":\"D\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }
}
