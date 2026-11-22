package za.co.mamadise.support_ticket_tracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.ResultActions;

import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.Role;
import za.co.mamadise.support_ticket_tracker.user.UserResponse;
import za.co.mamadise.support_ticket_tracker.user.UserService;

/**
 * Role-based authorization through the real filter chain, database and sessions: one account per role signs in for
 * real, and every request carries that account's own session.
 */
@org.springframework.context.annotation.Import(za.co.mamadise.support_ticket_tracker.TestDatabase.class)
@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationIntegrationTest {

    @Autowired
    private za.co.mamadise.support_ticket_tracker.TestDatabase database;

    private static final String PASSWORD = "correct horse 42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private AppUserRepository users;

    private UserResponse admin;
    private UserResponse agent;
    private UserResponse plain;

    @BeforeEach
    void createOneAccountPerRole() {
        database.reset();
        admin = userService.createUser("ada.admin", "ada@example.com", PASSWORD, Role.ADMIN);
        agent = userService.createUser("sam.agent", "sam@example.com", PASSWORD, Role.SUPPORT_AGENT);
        plain = userService.createUser("una.user", "una@example.com", PASSWORD);
    }

    @AfterEach
    void cleanUp() {
        database.reset();
    }

    private MockHttpSession signIn(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private ResultActions changeRole(MockHttpSession session, Long id, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + id + "/role").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- role loading ----

    @Test
    void loginAndMeReportTheRolePersistedForTheAccount() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sam.agent\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"));

        mockMvc.perform(get("/api/auth/me").session(signIn("ada.admin")))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/auth/me").session(signIn("una.user")))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void accountResponsesNeverContainSecrets() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(signIn("ada.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))));
    }

    // ---- unauthenticated: 401 ----

    @Test
    void anonymousCallersGet401OnAdministrativeEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mockMvc.perform(put("/api/admin/users/" + plain.id() + "/role").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/tickets/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    // ---- ADMIN ----

    @Test
    void adminReachesAdministrativeEndpointsAndEveryTicketEndpoint() throws Exception {
        MockHttpSession session = signIn("ada.admin");

        mockMvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/tickets").session(session)).andExpect(status().isOk());
        // 404 (not 403): the role check passed and the ticket simply does not exist.
        mockMvc.perform(delete("/api/tickets/9999").with(csrf()).session(session)).andExpect(status().isNotFound());
    }

    // ---- SUPPORT_AGENT ----

    @Test
    void supportAgentUsesTicketsButIsForbiddenFromAdministrationAndDeletion() throws Exception {
        MockHttpSession session = signIn("sam.agent");

        mockMvc.perform(get("/api/tickets").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
        mockMvc.perform(delete("/api/tickets/9999").with(csrf()).session(session)).andExpect(status().isForbidden());
        changeRole(session, plain.id(), "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
    }

    // ---- USER ----

    @Test
    void plainUserUsesTicketsButIsForbiddenFromAdministrationAndDeletion() throws Exception {
        MockHttpSession session = signIn("una.user");

        mockMvc.perform(get("/api/tickets").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tickets/9999").with(csrf()).session(session)).andExpect(status().isForbidden());
    }

    @Test
    void forbiddenAnswersAreFixedJsonWithoutDetails() throws Exception {
        mockMvc.perform(delete("/api/tickets/9999").with(csrf()).session(signIn("una.user")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(content().string(not(containsString("ADMIN"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    // ---- role assignment restrictions ----

    @Test
    void aUserCannotMakeThemselvesAdministrator() throws Exception {
        changeRole(signIn("una.user"), plain.id(), "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());

        assertThat(users.findById(plain.id()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void loginIgnoresARoleSuppliedByTheClient() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"una.user\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void roleChangeNeedsACsrfToken() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + plain.id() + "/role").session(signIn("ada.admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token"));
        assertThat(users.findById(plain.id()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void adminCanChangeAnotherAccountsRole() throws Exception {
        changeRole(signIn("ada.admin"), plain.id(), "{\"role\":\"SUPPORT_AGENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plain.id()))
                .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(users.findById(plain.id()).orElseThrow().getRole()).isEqualTo(Role.SUPPORT_AGENT);
    }

    @Test
    void adminCannotChangeTheirOwnRole() throws Exception {
        changeRole(signIn("ada.admin"), admin.id(), "{\"role\":\"USER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You cannot change your own role"));

        assertThat(users.findById(admin.id()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void invalidRoleValuesAreRejectedAndNothingChanges() throws Exception {
        MockHttpSession session = signIn("ada.admin");

        changeRole(session, plain.id(), "{\"role\":\"SUPERUSER\"}").andExpect(status().isBadRequest());
        changeRole(session, plain.id(), "{\"role\":\"admin\"}").andExpect(status().isBadRequest());
        changeRole(session, plain.id(), "{\"role\":null}").andExpect(status().isBadRequest());
        changeRole(session, plain.id(), "{}").andExpect(status().isBadRequest());
        changeRole(session, plain.id(), "{bad").andExpect(status().isBadRequest());

        assertThat(users.findById(plain.id()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void changingTheRoleOfAnUnknownAccountIs404() throws Exception {
        changeRole(signIn("ada.admin"), 987654L, "{\"role\":\"USER\"}").andExpect(status().isNotFound());
    }

    // ---- role changes reach live sessions ----

    @Test
    void aPromotionAppliesToTheTargetsExistingSessionWithoutSigningInAgain() throws Exception {
        MockHttpSession userSession = signIn("una.user");
        mockMvc.perform(get("/api/admin/users").session(userSession)).andExpect(status().isForbidden());

        changeRole(signIn("ada.admin"), plain.id(), "{\"role\":\"ADMIN\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").session(userSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(userSession)).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void aDemotionAppliesToTheTargetsExistingSession() throws Exception {
        MockHttpSession agentSession = signIn("sam.agent");
        mockMvc.perform(get("/api/tickets").session(agentSession)).andExpect(status().isOk());

        changeRole(signIn("ada.admin"), agent.id(), "{\"role\":\"USER\"}").andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").session(agentSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets").session(agentSession)).andExpect(status().isOk());
    }

    @Test
    void aDemotedAdministratorLosesAccessOnTheNextRequest() throws Exception {
        MockHttpSession adminSession = signIn("ada.admin");
        // A second administrator demotes the first (an administrator cannot demote themselves).
        userService.createUser("bob.admin", "bob@example.com", PASSWORD, Role.ADMIN);
        changeRole(signIn("bob.admin"), admin.id(), "{\"role\":\"USER\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").session(adminSession)).andExpect(status().isForbidden());
    }

    @Test
    void aDisabledOrDeletedAccountsSessionStopsWorkingImmediately() throws Exception {
        MockHttpSession disabledSession = signIn("sam.agent");
        MockHttpSession deletedSession = signIn("una.user");

        AppUser agentAccount = users.findById(agent.id()).orElseThrow();
        agentAccount.setEnabled(false);
        users.saveAndFlush(agentAccount);
        users.deleteById(plain.id());

        mockMvc.perform(get("/api/tickets").session(disabledSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets").session(deletedSession)).andExpect(status().isUnauthorized());
    }
}
