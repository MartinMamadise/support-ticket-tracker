package za.co.mamadise.support_ticket_tracker;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Base for API integration tests: the real filter chain, services and H2 database, with every request made in the
 * session of an account that signed in through {@code POST /api/auth/login}. Nothing about security is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestDatabase database;

    /** All Spring tests share one H2 database: leave it empty for the next class. */
    @AfterEach
    void resetDatabaseAfterTest() {
        database.reset();
    }

    protected MockHttpSession signIn(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + TestDatabase.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    protected ResultActions getAs(MockHttpSession session, String url) throws Exception {
        return mockMvc.perform(get(url).session(session));
    }

    protected ResultActions postAs(MockHttpSession session, String url, String json) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putAs(MockHttpSession session, String url, String json) throws Exception {
        return mockMvc.perform(put(url).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions deleteAs(MockHttpSession session, String url) throws Exception {
        return mockMvc.perform(delete(url).with(csrf()).session(session));
    }

    /** Creates a ticket as the session's account and returns its id. */
    protected long createTicket(MockHttpSession session, String title) throws Exception {
        String body = postAs(session, "/api/tickets",
                "{\"title\":\"" + title + "\",\"description\":\"Details\",\"priority\":\"MEDIUM\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
