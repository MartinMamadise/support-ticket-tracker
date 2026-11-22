package za.co.mamadise.support_ticket_tracker.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import za.co.mamadise.support_ticket_tracker.ApiTestSupport;
import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.Role;

/**
 * Ticket ownership and visibility through the real API (Command 7B): IDOR, horizontal and vertical privilege
 * escalation, internal-note confidentiality and attachment access. Accounts: two ordinary users, an agent in the
 * "Network" team, an agent outside it, and an administrator.
 */
class TicketAccessIntegrationTest extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private MockHttpSession alice;
    private MockHttpSession bob;
    private MockHttpSession netAgent;
    private MockHttpSession otherAgent;
    private MockHttpSession admin;
    private AppUser netAgentAccount;
    private Team network;

    @BeforeEach
    void accounts() throws Exception {
        database.reset();
        database.user("alice", Role.USER);
        database.user("bob", Role.USER);
        netAgentAccount = database.user("nina.net", Role.SUPPORT_AGENT);
        database.user("oscar.other", Role.SUPPORT_AGENT);
        database.user("ada.admin", Role.ADMIN);
        network = database.team("Network", netAgentAccount);
        alice = signIn("alice");
        bob = signIn("bob");
        netAgent = signIn("nina.net");
        otherAgent = signIn("oscar.other");
        admin = signIn("ada.admin");
    }

    private void assignToNetwork(long ticketId) throws Exception {
        putAs(admin, "/api/tickets/" + ticketId + "/assignment", "{\"teamId\":" + network.getId() + "}")
                .andExpect(status().isOk());
    }

    @Test
    void usersCannotReadOrChangeEachOthersTicketsAndCannotTellThemApartFromMissingOnes() throws Exception {
        long ticket = createTicket(alice, "Alice laptop");
        String url = "/api/tickets/" + ticket;

        getAs(bob, url).andExpect(status().isNotFound());
        getAs(bob, "/api/tickets/999999").andExpect(status().isNotFound());
        putAs(bob, url, "{\"title\":\"x\",\"description\":\"y\",\"priority\":\"LOW\"}").andExpect(status().isNotFound());
        putAs(bob, url + "/status", "{\"status\":\"CANCELLED\"}").andExpect(status().isNotFound());
        getAs(bob, url + "/comments").andExpect(status().isNotFound());
        postAs(bob, url + "/comments", "{\"body\":\"hi\"}").andExpect(status().isNotFound());
        getAs(bob, url + "/activity").andExpect(status().isNotFound());
        getAs(bob, url + "/attachments").andExpect(status().isNotFound());

        getAs(bob, "/api/tickets").andExpect(jsonPath("$.totalElements").value(0));
        getAs(bob, "/api/tickets?requesterId=" + 1).andExpect(jsonPath("$.totalElements").value(0));
        getAs(alice, "/api/tickets").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ordinaryUsersCannotUseStaffOrAdministratorEndpoints() throws Exception {
        long ticket = createTicket(alice, "Alice VPN");
        putAs(alice, "/api/tickets/" + ticket + "/assignment", "{\"teamId\":" + network.getId() + "}")
                .andExpect(status().isForbidden());
        getAs(alice, "/api/teams").andExpect(status().isForbidden());
        getAs(alice, "/api/users/assignable").andExpect(status().isForbidden());
        getAs(alice, "/api/workload").andExpect(status().isForbidden());
        getAs(alice, "/api/admin/users").andExpect(status().isForbidden());
        postAs(alice, "/api/admin/users", "{\"username\":\"evil\",\"email\":\"e@example.com\",\"password\":\"Password123x\","
                + "\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
        putAs(alice, "/api/admin/sla-policies/LOW", "{\"responseMinutes\":1,\"resolutionMinutes\":1}")
                .andExpect(status().isForbidden());
        deleteAs(alice, "/api/tickets/" + ticket).andExpect(status().isForbidden());
    }

    @Test
    void agentsSeeTheirTeamsTicketsButNotOtherTeams() throws Exception {
        long queued = createTicket(alice, "General queue");
        long teamTicket = createTicket(alice, "Network ticket");
        assignToNetwork(teamTicket);

        getAs(otherAgent, "/api/tickets/" + queued).andExpect(status().isOk());
        getAs(otherAgent, "/api/tickets/" + teamTicket).andExpect(status().isNotFound());
        getAs(netAgent, "/api/tickets/" + teamTicket).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.canAddInternalNote").value(true));
        getAs(netAgent, "/api/tickets?view=team").andExpect(jsonPath("$.totalElements").value(1));
        putAs(otherAgent, "/api/tickets/" + teamTicket + "/status", "{\"status\":\"IN_PROGRESS\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void internalNotesNeverReachTheRequester() throws Exception {
        long ticket = createTicket(alice, "Printer");
        String url = "/api/tickets/" + ticket;
        long note = id(postAs(netAgent, url + "/comments", "{\"body\":\"SECRET-NOTE-TEXT\",\"internal\":true}")
                .andExpect(status().isCreated()));
        postAs(netAgent, url + "/comments", "{\"body\":\"We are on it\"}").andExpect(status().isCreated());

        getAs(alice, url + "/comments").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(content().string(not(containsString("SECRET-NOTE-TEXT"))));
        getAs(alice, url + "/activity").andExpect(status().isOk())
                .andExpect(content().string(not(containsString("INTERNAL_NOTE"))));
        getAs(alice, "/api/notifications").andExpect(content().string(not(containsString("SECRET-NOTE-TEXT"))))
                .andExpect(content().string(not(containsString("Internal note"))));
        putAs(alice, url + "/comments/" + note, "{\"body\":\"edit\"}").andExpect(status().isNotFound());

        getAs(netAgent, url + "/comments").andExpect(jsonPath("$", hasSize(2)));
        getAs(admin, url + "/comments").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void requestersCannotWriteInternalNotesOrResolveTheirOwnTickets() throws Exception {
        long ticket = createTicket(alice, "Mail");
        String url = "/api/tickets/" + ticket;
        postAs(alice, url + "/comments", "{\"body\":\"sneaky\",\"internal\":true}").andExpect(status().isForbidden());
        putAs(netAgent, url + "/status", "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        putAs(alice, url + "/status", "{\"status\":\"RESOLVED\"}").andExpect(status().isForbidden());
        putAs(alice, url + "/status", "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
    }

    @Test
    void requesterCanReopenAResolvedTicket() throws Exception {
        long ticket = createTicket(alice, "Wifi");
        String url = "/api/tickets/" + ticket;
        putAs(netAgent, url + "/status", "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        putAs(netAgent, url + "/status", "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());
        getAs(alice, url).andExpect(jsonPath("$.allowedTransitions", hasSize(2)));
        putAs(alice, url + "/status", "{\"status\":\"OPEN\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
        getAs(alice, url + "/activity").andExpect(content().string(containsString("REOPENED")));
    }

    @Test
    void attachmentsFollowTheTicketsVisibility() throws Exception {
        long ticket = createTicket(alice, "Screenshot");
        String url = "/api/tickets/" + ticket + "/attachments";
        String body = mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "../../error.png", "text/html", PNG))
                        .with(csrf()).session(alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("error.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        long attachment = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        getAs(alice, url + "/" + attachment + "/download").andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("attachment")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"));
        getAs(bob, url + "/" + attachment + "/download").andExpect(status().isNotFound());
        getAs(bob, "/api/tickets/" + (ticket + 1) + "/attachments/" + attachment + "/download")
                .andExpect(status().isNotFound());
        deleteAs(bob, url + "/" + attachment).andExpect(status().isNotFound());
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "x.png", "image/png", PNG))
                .with(csrf()).session(bob)).andExpect(status().isNotFound());
    }

    @Test
    void dangerousOrDisguisedUploadsAreRejected() throws Exception {
        long ticket = createTicket(alice, "Upload");
        String url = "/api/tickets/" + ticket + "/attachments";
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "tool.exe", "image/png", PNG))
                .with(csrf()).session(alice)).andExpect(status().isBadRequest());
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "page.png", "image/png",
                "<html><script>alert(1)</script>".getBytes())).with(csrf()).session(alice))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .with(csrf()).session(alice)).andExpect(status().isBadRequest());
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "x.png", "image/png", PNG)).session(alice))
                .andExpect(status().isForbidden()); // no CSRF token
    }

    @Test
    void dashboardStatisticsAreScopedToWhatTheCallerMaySee() throws Exception {
        createTicket(alice, "A1");
        createTicket(alice, "A2");
        createTicket(bob, "B1");

        getAs(alice, "/api/dashboard").andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("own"))
                .andExpect(jsonPath("$.totals.total").value(2))
                .andExpect(jsonPath("$.workload").doesNotExist());
        getAs(admin, "/api/dashboard").andExpect(jsonPath("$.totals.total").value(3))
                .andExpect(jsonPath("$.totals.unassigned").value(3))
                .andExpect(jsonPath("$.byStatus.OPEN").value(3))
                .andExpect(jsonPath("$.workload.unassignedTickets").value(3))
                .andExpect(jsonPath("$.trend", hasSize(14)));
    }

    @Test
    void notificationsBelongToTheirRecipientOnly() throws Exception {
        long ticket = createTicket(alice, "Phone");
        putAs(admin, "/api/tickets/" + ticket + "/assignment", "{\"assigneeId\":" + netAgentAccount.getId() + "}")
                .andExpect(status().isOk());
        String body = getAs(netAgent, "/api/notifications").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TICKET_ASSIGNED"))
                .andReturn().getResponse().getContentAsString();
        long notification = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.content[0].id")).longValue();

        putAs(bob, "/api/notifications/" + notification + "/read", "").andExpect(status().isNotFound());
        getAs(netAgent, "/api/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
        putAs(netAgent, "/api/notifications/" + notification + "/read", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        getAs(netAgent, "/api/notifications/unread-count").andExpect(jsonPath("$.count").value(0));
    }
}
