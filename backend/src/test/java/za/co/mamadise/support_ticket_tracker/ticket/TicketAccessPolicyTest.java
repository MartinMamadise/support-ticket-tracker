package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.Role;

/** The permission matrix of TicketAccessPolicy, one rule per test. */
class TicketAccessPolicyTest {

    private static final Viewer ADMIN = new Viewer(1L, Role.ADMIN, Set.of());
    private static final Viewer AGENT_IN_TEAM = new Viewer(2L, Role.SUPPORT_AGENT, Set.of(10L));
    private static final Viewer AGENT_ELSEWHERE = new Viewer(5L, Role.SUPPORT_AGENT, Set.of(20L));
    private static final Viewer REQUESTER = new Viewer(3L, Role.USER, Set.of());
    private static final Viewer OTHER_USER = new Viewer(4L, Role.USER, Set.of());

    private static AppUser account(long id, Role role) {
        AppUser user = new AppUser("u" + id, "u" + id + "@example.com", "$2a$04$x", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Team team(long id) {
        Team team = new Team("T" + id, null);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private static Ticket ticket(TicketStatus status, Long teamId, Long assigneeId) {
        Ticket t = new Ticket();
        t.setRequester(account(3L, Role.USER));
        if (teamId != null) {
            t.setTeam(team(teamId));
        }
        if (assigneeId != null) {
            t.setAssignee(account(assigneeId, Role.SUPPORT_AGENT));
        }
        ReflectionTestUtils.setField(t, "status", status);
        return t;
    }

    @Test
    void usersSeeOnlyTheirOwnTickets() {
        Ticket t = ticket(TicketStatus.OPEN, null, null);
        assertThat(TicketAccessPolicy.canView(REQUESTER, t)).isTrue();
        assertThat(TicketAccessPolicy.canView(OTHER_USER, t)).isFalse();
    }

    @Test
    void agentsSeeAssignedTeamAndGeneralQueueTicketsOnly() {
        assertThat(TicketAccessPolicy.canView(AGENT_ELSEWHERE, ticket(TicketStatus.OPEN, null, null))).as("queue").isTrue();
        assertThat(TicketAccessPolicy.canView(AGENT_ELSEWHERE, ticket(TicketStatus.OPEN, 10L, null))).as("other team")
                .isFalse();
        assertThat(TicketAccessPolicy.canView(AGENT_IN_TEAM, ticket(TicketStatus.OPEN, 10L, null))).as("own team").isTrue();
        assertThat(TicketAccessPolicy.canView(AGENT_ELSEWHERE, ticket(TicketStatus.OPEN, 10L, 5L))).as("assigned")
                .isTrue();
        assertThat(TicketAccessPolicy.canView(AGENT_ELSEWHERE, ticket(TicketStatus.OPEN, null, 2L)))
                .as("assigned to someone else, no team").isFalse();
    }

    @Test
    void adminsSeeEverything() {
        assertThat(TicketAccessPolicy.canView(ADMIN, ticket(TicketStatus.CLOSED, 99L, 77L))).isTrue();
    }

    @Test
    void internalNotesAreForStaffWithWorkAccessOnly() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS, 10L, null);
        assertThat(TicketAccessPolicy.canSeeInternal(REQUESTER, t)).isFalse();
        assertThat(TicketAccessPolicy.canAddInternalNote(REQUESTER, t)).isFalse();
        assertThat(TicketAccessPolicy.canSeeInternal(AGENT_IN_TEAM, t)).isTrue();
        assertThat(TicketAccessPolicy.canSeeInternal(ADMIN, t)).isTrue();
    }

    @Test
    void anAgentWhoRaisedATicketIsTreatedAsItsRequester() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS, 10L, null);
        t.setRequester(account(5L, Role.SUPPORT_AGENT));
        assertThat(TicketAccessPolicy.canView(AGENT_ELSEWHERE, t)).isTrue();
        assertThat(TicketAccessPolicy.hasWorkAccess(AGENT_ELSEWHERE, t)).isFalse();
        assertThat(TicketAccessPolicy.canSeeInternal(AGENT_ELSEWHERE, t)).isFalse();
    }

    @Test
    void requesterStatusMoves() {
        assertThat(TicketAccessPolicy.allowedTransitions(REQUESTER, ticket(TicketStatus.OPEN, null, null)))
                .containsExactly(TicketStatus.CANCELLED);
        assertThat(TicketAccessPolicy.allowedTransitions(REQUESTER, ticket(TicketStatus.WAITING, null, null)))
                .containsExactly(TicketStatus.CANCELLED);
        assertThat(TicketAccessPolicy.allowedTransitions(REQUESTER, ticket(TicketStatus.RESOLVED, null, null)))
                .containsExactlyInAnyOrder(TicketStatus.OPEN, TicketStatus.CLOSED);
        assertThat(TicketAccessPolicy.allowedTransitions(REQUESTER, ticket(TicketStatus.CLOSED, null, null))).isEmpty();
        assertThat(TicketAccessPolicy.allowedTransitions(OTHER_USER, ticket(TicketStatus.RESOLVED, null, null))).isEmpty();
    }

    @Test
    void staffWithWorkAccessGetTheWholeWorkflow() {
        assertThat(TicketAccessPolicy.allowedTransitions(AGENT_IN_TEAM, ticket(TicketStatus.IN_PROGRESS, 10L, null)))
                .isEqualTo(TicketStatus.IN_PROGRESS.allowedTransitions());
    }

    @Test
    void detailsAreEditableByTheRequesterOnlyWhileOpenAndByNobodyOnceFinal() {
        assertThat(TicketAccessPolicy.canEditDetails(REQUESTER, ticket(TicketStatus.OPEN, null, null))).isTrue();
        assertThat(TicketAccessPolicy.canEditDetails(REQUESTER, ticket(TicketStatus.IN_PROGRESS, null, null))).isFalse();
        assertThat(TicketAccessPolicy.canEditDetails(ADMIN, ticket(TicketStatus.IN_PROGRESS, null, null))).isTrue();
        assertThat(TicketAccessPolicy.canEditDetails(ADMIN, ticket(TicketStatus.CANCELLED, null, null))).isFalse();
        assertThat(TicketAccessPolicy.canComment(REQUESTER, ticket(TicketStatus.CLOSED, null, null))).isFalse();
    }

    @Test
    void onlyStaffWithWorkAccessAssignAndOnlyAdminsDelete() {
        Ticket t = ticket(TicketStatus.OPEN, 10L, null);
        assertThat(TicketAccessPolicy.canAssign(AGENT_IN_TEAM, t)).isTrue();
        assertThat(TicketAccessPolicy.canAssign(AGENT_ELSEWHERE, t)).isFalse();
        assertThat(TicketAccessPolicy.canAssign(REQUESTER, t)).isFalse();
        assertThat(TicketAccessPolicy.canDelete(ADMIN)).isTrue();
        assertThat(TicketAccessPolicy.canDelete(AGENT_IN_TEAM)).isFalse();
    }
}
