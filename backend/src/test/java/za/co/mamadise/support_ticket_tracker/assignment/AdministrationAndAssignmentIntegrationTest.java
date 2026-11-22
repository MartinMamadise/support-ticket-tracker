package za.co.mamadise.support_ticket_tracker.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import za.co.mamadise.support_ticket_tracker.ApiTestSupport;
import za.co.mamadise.support_ticket_tracker.TestDatabase;
import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.Role;

/** User and team administration (Command 8) and the assignment workflow (Command 9) through the real API. */
class AdministrationAndAssignmentIntegrationTest extends ApiTestSupport {

    @Autowired
    private TicketRepository tickets;

    private MockHttpSession admin;
    private MockHttpSession agent;
    private MockHttpSession user;
    private AppUser adminAccount;
    private AppUser agentAccount;
    private AppUser userAccount;
    private Team desk;

    @BeforeEach
    void accounts() throws Exception {
        database.reset();
        adminAccount = database.user("ada.admin", Role.ADMIN);
        agentAccount = database.user("sam.agent", Role.SUPPORT_AGENT);
        userAccount = database.user("una.user", Role.USER);
        desk = database.team("Service Desk", agentAccount);
        admin = signIn("ada.admin");
        agent = signIn("sam.agent");
        user = signIn("una.user");
    }

    // ---- users ----

    @Test
    void adminCreatesAccountsThatDefaultToTheLeastPrivilegedRole() throws Exception {
        postAs(admin, "/api/admin/users", """
                {"username":"New.Person","email":"New@Example.com","fullName":"  New   Person ","password":"Welcome-2026x"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.person"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.fullName").value("New Person"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("Welcome-2026x"))));
        signIn("new.person", "Welcome-2026x");

        postAs(admin, "/api/admin/users", """
                {"username":"weak","email":"weak@example.com","password":"short"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
        postAs(admin, "/api/admin/users", """
                {"username":"sam.agent","email":"other@example.com","password":"Welcome-2026x"}""")
                .andExpect(status().isConflict());
    }

    @Test
    void adminListsAndSearchesAccountsPaged() throws Exception {
        getAs(admin, "/api/admin/users?q=SAM").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("sam.agent"));
        getAs(admin, "/api/admin/users?role=USER").andExpect(jsonPath("$.totalElements").value(1));
        getAs(admin, "/api/admin/users?size=2").andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void adminCannotDemoteOrDeactivateThemselves() throws Exception {
        String self = "/api/admin/users/" + adminAccount.getId();
        putAs(admin, self, "{\"email\":\"ada.admin@example.com\",\"role\":\"USER\",\"enabled\":true}")
                .andExpect(status().isConflict());
        putAs(admin, self, "{\"email\":\"ada.admin@example.com\",\"role\":\"ADMIN\",\"enabled\":false}")
                .andExpect(status().isConflict());
    }

    @Test
    void deactivatingAnAgentEndsTheirSessionAndRemovesThemFromTheirTickets() throws Exception {
        long ticket = createTicket(user, "Keyboard");
        putAs(admin, "/api/tickets/" + ticket + "/assignment",
                "{\"assigneeId\":" + agentAccount.getId() + ",\"teamId\":" + desk.getId() + "}").andExpect(status().isOk());

        putAs(admin, "/api/admin/users/" + agentAccount.getId(),
                "{\"email\":\"sam.agent@example.com\",\"role\":\"SUPPORT_AGENT\",\"enabled\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

        getAs(agent, "/api/tickets").andExpect(status().isUnauthorized());
        getAs(admin, "/api/tickets/" + ticket).andExpect(jsonPath("$.assignee").doesNotExist())
                .andExpect(jsonPath("$.team.name").value("Service Desk"));
        putAs(admin, "/api/tickets/" + ticket + "/assignment", "{\"assigneeId\":" + agentAccount.getId() + "}")
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sam.agent\",\"password\":\"" + TestDatabase.PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsCanBeResetByAdminsAndChangedByTheirOwners() throws Exception {
        putAs(admin, "/api/admin/users/" + userAccount.getId() + "/password", "{\"newPassword\":\"Brand-new-pass-9\"}")
                .andExpect(status().isNoContent());
        putAs(user, "/api/profile/password", "{\"currentPassword\":\"wrong\",\"newPassword\":\"Another-pass-77\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
        putAs(user, "/api/profile/password",
                "{\"currentPassword\":\"Brand-new-pass-9\",\"newPassword\":\"Another-pass-77\"}")
                .andExpect(status().isNoContent());
        putAs(user, "/api/profile", "{\"email\":\"una.new@example.com\",\"fullName\":\"Una User\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Una User"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    // ---- teams ----

    @Test
    void teamMembersMustBeActiveStaff() throws Exception {
        String members = "/api/admin/teams/" + desk.getId() + "/members";
        putAs(admin, members, "{\"userIds\":[" + userAccount.getId() + "]}").andExpect(status().isBadRequest());
        putAs(admin, members, "{\"userIds\":[987654]}").andExpect(status().isBadRequest());
        putAs(admin, members, "{\"userIds\":[" + agentAccount.getId() + "," + adminAccount.getId() + "]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.members", hasSize(2)));
        putAs(agent, members, "{\"userIds\":[]}").andExpect(status().isForbidden());
    }

    @Test
    void teamNamesAreUniqueAndInactiveTeamsReceiveNoTickets() throws Exception {
        postAs(admin, "/api/admin/teams", "{\"name\":\"service desk\"}").andExpect(status().isConflict());
        long network = id(postAs(admin, "/api/admin/teams", "{\"name\":\"Network\",\"description\":\"LAN/WAN\"}")
                .andExpect(status().isCreated()));
        putAs(admin, "/api/admin/teams/" + network, "{\"name\":\"Network\",\"active\":false}").andExpect(status().isOk());

        long ticket = createTicket(user, "Switch down");
        putAs(admin, "/api/tickets/" + ticket + "/assignment", "{\"teamId\":" + network + "}")
                .andExpect(status().isConflict());
        getAs(agent, "/api/teams").andExpect(jsonPath("$", hasSize(1)));
        getAs(admin, "/api/teams?includeInactive=true").andExpect(jsonPath("$", hasSize(2)));
    }

    // ---- assignment ----

    @Test
    void assignmentIsValidatedRecordedAndNotified() throws Exception {
        long ticket = createTicket(user, "Monitor flickers");
        String url = "/api/tickets/" + ticket;

        putAs(agent, url + "/assignment", "{\"assigneeId\":" + userAccount.getId() + "}")
                .andExpect(status().isBadRequest());
        putAs(agent, url + "/assignment", "{\"assigneeId\":" + adminAccount.getId() + ",\"teamId\":" + desk.getId() + "}")
                .andExpect(status().isBadRequest());
        putAs(agent, url + "/assignment", "{\"assigneeId\":" + agentAccount.getId() + ",\"teamId\":" + desk.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.username").value("sam.agent"))
                .andExpect(jsonPath("$.team.name").value("Service Desk"));
        putAs(admin, url + "/assignment", "{\"assigneeId\":" + adminAccount.getId() + "}").andExpect(status().isOk());

        getAs(agent, url + "/assignments").andExpect(status().isNotFound()); // no longer in the agent's queue at all
        getAs(admin, url + "/assignments").andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].newAssignee.username").value("sam.agent"))
                .andExpect(jsonPath("$[1].previousAssignee.username").value("sam.agent"))
                .andExpect(jsonPath("$[1].newAssignee.username").value("ada.admin"))
                .andExpect(jsonPath("$[1].previousTeam.name").value("Service Desk"))
                .andExpect(jsonPath("$[1].newTeam").doesNotExist());
        getAs(admin, url + "/activity").andExpect(content().string(containsString("ASSIGNEE_CHANGED")));
        // Self-assignment by sam is not notified to sam; the reassignment away from sam is.
        getAs(agent, "/api/notifications").andExpect(jsonPath("$.content[0].type").value("TICKET_UNASSIGNED"));
        getAs(user, url + "/assignments").andExpect(status().isForbidden());
    }

    @Test
    void finalTicketsCannotBeReassigned() throws Exception {
        long ticket = createTicket(user, "Done");
        putAs(user, "/api/tickets/" + ticket + "/status", "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
        putAs(admin, "/api/tickets/" + ticket + "/assignment", "{\"teamId\":" + desk.getId() + "}")
                .andExpect(status().isConflict());
    }

    @Test
    void workloadCountsActiveTicketsPerAgentAndTeam() throws Exception {
        for (int i = 0; i < 3; i++) {
            long ticket = createTicket(user, "T" + i);
            putAs(admin, "/api/tickets/" + ticket + "/assignment",
                    "{\"assigneeId\":" + agentAccount.getId() + ",\"teamId\":" + desk.getId() + "}").andExpect(status().isOk());
        }
        createTicket(user, "Unassigned");
        assertThat(tickets.count()).isEqualTo(4);

        getAs(agent, "/api/workload").andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedTickets").value(1))
                .andExpect(jsonPath("$.agents[0].username").value("sam.agent"))
                .andExpect(jsonPath("$.agents[0].activeTickets").value(3))
                .andExpect(jsonPath("$.agents[0].level").value("LOW"))
                .andExpect(jsonPath("$.teams[0].activeTickets").value(3));
        getAs(agent, "/api/tickets?view=assigned").andExpect(jsonPath("$.totalElements").value(3));
        getAs(agent, "/api/tickets?view=unassigned").andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---- comments ----

    @Test
    void requesterReplyToAWaitingTicketPutsItBackInProgress() throws Exception {
        long ticket = createTicket(user, "Need info");
        String url = "/api/tickets/" + ticket;
        putAs(agent, url + "/status", "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        putAs(agent, url + "/status", "{\"status\":\"WAITING\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.resolutionStatus").value("PAUSED"));
        postAs(user, url + "/comments", "{\"body\":\"Here is the info\"}").andExpect(status().isCreated());
        getAs(user, url).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.sla.paused").value(false));
    }

    @Test
    void commentsAreEditableByTheirAuthorAndDeletableByAdminsOnly() throws Exception {
        long ticket = createTicket(user, "Comment rules");
        String url = "/api/tickets/" + ticket + "/comments";
        long comment = id(postAs(user, url, "{\"body\":\"First\"}").andExpect(status().isCreated()));
        putAs(agent, url + "/" + comment, "{\"body\":\"hijack\"}").andExpect(status().isForbidden());
        putAs(user, url + "/" + comment, "{\"body\":\"First (edited)\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true));
        deleteAs(user, url + "/" + comment).andExpect(status().isForbidden());
        deleteAs(admin, url + "/" + comment).andExpect(status().isNoContent());
        postAs(user, url, "{\"body\":\"   \"}").andExpect(status().isBadRequest());
    }
}
