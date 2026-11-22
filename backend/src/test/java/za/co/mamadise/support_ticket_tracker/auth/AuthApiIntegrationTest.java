package za.co.mamadise.support_ticket_tracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.UserService;

/** Login, current user and logout through the real security filter chain, database and password hashing. */
@org.springframework.context.annotation.Import(za.co.mamadise.support_ticket_tracker.TestDatabase.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired
    private za.co.mamadise.support_ticket_tracker.TestDatabase database;

    private static final String PASSWORD = "correct horse 42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void createAccount() {
        database.reset();
        userService.createUser("thandi", "thandi@example.com", PASSWORD);
    }

    @AfterEach
    void removeAccounts() {
        database.reset();
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpSession loginSession() throws Exception {
        MvcResult result = login("thandi", PASSWORD).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void successfulLoginReturnsSafeUserAndStartsAnHttpOnlySession() throws Exception {
        login("thandi", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("thandi"))
                .andExpect(jsonPath("$.email").value("thandi@example.com"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("$2"))))
                .andExpect(cookie().exists("JSESSIONID"))
                .andExpect(cookie().httpOnly("JSESSIONID", true));
    }

    @Test
    void loginIsCaseInsensitiveAndAcceptsTheEmailAddress() throws Exception {
        login("  THANDI ", PASSWORD).andExpect(status().isOk());
        login("Thandi@Example.com", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("thandi"));
    }

    @Test
    void wrongPasswordIsRejectedWithAGenericMessage() throws Exception {
        login("thandi", "wrong password 1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void unknownUserGetsExactlyTheSameAnswerAsAWrongPassword() throws Exception {
        String wrongPassword = login("thandi", "wrong password 1").andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody", "wrong password 1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andReturn().getResponse().getContentAsString();

        // Only the timestamp may differ.
        assertThat(strip(unknownUser)).isEqualTo(strip(wrongPassword));
        login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotSignInEvenWithTheRightPasswordAndLooksLikeAnyOtherFailure() throws Exception {
        AppUser user = users.findByUsername("thandi").orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);

        login("thandi", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void loginValidatesItsBody() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void currentUserIsReturnedForAValidSessionWithoutSecrets() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("thandi"))
                .andExpect(jsonPath("$.email").value("thandi@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))));
    }

    @Test
    void currentUserWithoutASessionIsUnauthorizedInTheStandardErrorShape() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void sessionOfAnAccountDisabledAfterLoginIsEnded() throws Exception {
        MockHttpSession session = loginSession();
        AppUser user = users.findByUsername("thandi").orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionOfADeletedAccountIsRejected() throws Exception {
        MockHttpSession session = loginSession();
        database.reset();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session is no longer valid"));
    }

    @Test
    void logoutEndsTheSessionSoItCannotBeUsedAgain() throws Exception {
        MockHttpSession session = loginSession();
        mockMvc.perform(get("/api/tickets").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutASessionIsHarmless() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void logoutNeedsACsrfToken() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void sessionIdChangesWhenLoggingInWithAnExistingSession() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String oldId = before.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).session(before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(oldId);
    }

    /** Removes the timestamp so two error bodies can be compared. */
    private static String strip(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\",?", "");
    }
}
