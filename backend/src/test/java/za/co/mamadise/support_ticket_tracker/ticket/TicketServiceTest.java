package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.attachment.AttachmentService;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.ForbiddenException;
import za.co.mamadise.support_ticket_tracker.notification.NotificationService;
import za.co.mamadise.support_ticket_tracker.sla.SlaPolicyRepository;
import za.co.mamadise.support_ticket_tracker.sla.SlaService;
import za.co.mamadise.support_ticket_tracker.sla.SlaStatus;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.Role;

/**
 * TicketService with the real access policy, SLA rules and status-change logic; only persistence, activity,
 * notifications and the caller lookup are mocked. SLA targets are the built-in defaults (no policy rows).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-11-21T08:00:00Z");

    private static final Viewer ADMIN = new Viewer(1L, Role.ADMIN, Set.of());
    private static final Viewer AGENT = new Viewer(2L, Role.SUPPORT_AGENT, Set.of(10L));
    private static final Viewer REQUESTER = new Viewer(3L, Role.USER, Set.of());
    private static final Viewer STRANGER = new Viewer(4L, Role.USER, Set.of());

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AppUserRepository users;
    @Mock
    private SlaPolicyRepository slaPolicies;
    @Mock
    private ActivityService activity;
    @Mock
    private NotificationService notifications;
    @Mock
    private AttachmentService attachments;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        SlaService sla = new SlaService(slaPolicies); // empty repository: default targets
        ticketService = new TicketService(ticketRepository, new TicketLookup(ticketRepository),
                new TicketStatusChanger(sla, activity, notifications), currentUser, users, sla, activity, attachments,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", 99L);
                ReflectionTestUtils.setField(t, "version", 0L);
            }
            return t;
        });
        when(users.getReferenceById(anyLong())).thenAnswer(inv -> account(inv.getArgument(0), Role.USER));
    }

    private void signedIn(Viewer viewer) {
        when(currentUser.require()).thenReturn(viewer);
    }

    private static AppUser account(long id, Role role) {
        AppUser user = new AppUser("user" + id, "user" + id + "@example.com", "$2a$04$hash-not-used-in-this-test", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** A stored ticket raised by REQUESTER, already in the given status, with SLA clocks started at EARLIER. */
    private Ticket existing(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setCreatedAt(EARLIER);
        ticket.setTitle("Old title");
        ticket.setDescription("Old description");
        ticket.setPriority(TicketPriority.LOW);
        ticket.setRequester(account(REQUESTER.id(), Role.USER));
        ticket.startSla(new SlaService(slaPolicies).targetFor(TicketPriority.LOW), EARLIER);
        ReflectionTestUtils.setField(ticket, "id", 1L);
        ReflectionTestUtils.setField(ticket, "version", 3L);
        ReflectionTestUtils.setField(ticket, "status", status);
        if (status.isResolution()) {
            ReflectionTestUtils.setField(ticket, "resolvedAt", EARLIER.plusSeconds(60));
        }
        when(ticketRepository.findDetailedById(1L)).thenReturn(Optional.of(ticket));
        return ticket;
    }

    private static TicketRequest details(String title, String status, String priority) {
        return new TicketRequest(title, "Old description", status, priority, null, null);
    }

    // ---- create ----

    @Test
    void createMakesTheCallerTheRequesterStartsOpenAndStartsTheSlaClocks() {
        signedIn(REQUESTER);

        TicketResponse created = ticketService.createTicket(
                new TicketRequest("  Printer broken ", " Paper jam ", null, "HIGH", "HARDWARE", null));

        assertThat(created.title()).isEqualTo("Printer broken");
        assertThat(created.description()).isEqualTo("Paper jam");
        assertThat(created.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(created.category()).isEqualTo(TicketCategory.HARDWARE);
        assertThat(created.requester().id()).isEqualTo(REQUESTER.id());
        assertThat(created.assignee()).isNull();
        // Default HIGH targets: response 60 min, resolution 480 min.
        assertThat(created.sla().responseDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(created.sla().resolutionDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(480)));
        assertThat(created.sla().resolutionStatus()).isEqualTo(SlaStatus.ON_TRACK);
        verify(activity).record(99L, REQUESTER.id(), ActivityType.CREATED);
    }

    @Test
    void categoryDefaultsToOther() {
        signedIn(REQUESTER);
        assertThat(ticketService.createTicket(new TicketRequest("T", "D", null, "LOW", null, null)).category())
                .isEqualTo(TicketCategory.OTHER);
    }

    @Test
    void createRejectsAnyStatusOtherThanOpen() {
        signedIn(ADMIN);
        for (String status : new String[] { "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED", "CANCELLED" }) {
            assertThatThrownBy(() -> ticketService.createTicket(new TicketRequest("T", "D", status, "LOW", null, null)))
                    .as(status)
                    .isInstanceOf(TicketWorkflowException.class)
                    .hasMessageContaining("must start as OPEN");
        }
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    // ---- visibility ----

    @Test
    void anotherUsersTicketIsReportedAsNotFound() {
        existing(TicketStatus.OPEN);
        signedIn(STRANGER);

        assertThatThrownBy(() -> ticketService.getTicketById(1L)).isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> ticketService.updateTicket(1L, details("Hijack", null, "LOW")))
                .isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> ticketService.changeStatus(1L, new StatusChangeRequest("CANCELLED", null)))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void anAgentSeesTheGeneralQueueButNotAnotherTeamsTicket() {
        Ticket ticket = existing(TicketStatus.OPEN);
        signedIn(AGENT);
        assertThat(ticketService.getTicketById(1L).id()).isEqualTo(1L); // no assignee, no team: general queue

        ReflectionTestUtils.setField(ticket, "team", teamWithId(20L)); // a team the agent is not in
        assertThatThrownBy(() -> ticketService.getTicketById(1L)).isInstanceOf(TicketNotFoundException.class);

        ReflectionTestUtils.setField(ticket, "team", teamWithId(10L)); // the agent's team
        assertThat(ticketService.getTicketById(1L).permissions().canAddInternalNote()).isTrue();
    }

    private static za.co.mamadise.support_ticket_tracker.team.Team teamWithId(long id) {
        var team = new za.co.mamadise.support_ticket_tracker.team.Team("Team " + id, null);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTicketsUsesTheQueryPagingAndMapsEntities() {
        signedIn(ADMIN);
        TicketListQuery query = TicketListQuery.parse("1", "5", "priority", "asc", null, null, null, null, null);
        Page<Ticket> page = new PageImpl<>(List.of(existing(TicketStatus.OPEN)), query.toPageable(), 6);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<TicketResponse> result = ticketService.listTickets(query);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("priorityRank").isAscending()).isTrue();
        assertThat(result.content()).extracting(TicketResponse::id).containsExactly(1L);
        assertThat(result.totalElements()).isEqualTo(6);
    }

    // ---- requester rules ----

    @Test
    void requesterMayEditDetailsWhileTheTicketIsOpen() {
        existing(TicketStatus.OPEN);
        signedIn(REQUESTER);

        TicketResponse updated = ticketService.updateTicket(1L, details("New title", null, "LOW"));

        assertThat(updated.title()).isEqualTo("New title");
        verify(activity).record(1L, REQUESTER.id(), ActivityType.UPDATED);
    }

    @Test
    void requesterMayNotEditDetailsOnceWorkHasStarted() {
        Ticket ticket = existing(TicketStatus.IN_PROGRESS);
        signedIn(REQUESTER);

        assertThatThrownBy(() -> ticketService.updateTicket(1L, details("New title", null, "LOW")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ticket.getTitle()).isEqualTo("Old title");
    }

    @Test
    void requesterMayReopenAResolvedTicketWhichRestartsTheResolutionClock() {
        Ticket ticket = existing(TicketStatus.RESOLVED);
        signedIn(REQUESTER);

        TicketResponse reopened = ticketService.changeStatus(1L, new StatusChangeRequest("OPEN", 3L));

        assertThat(reopened.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(reopened.resolvedAt()).isNull();
        assertThat(ticket.getSlaStartedAt()).isEqualTo(NOW);
        assertThat(ticket.getResolutionDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4320))); // LOW: 72 h
        verify(activity).recordChange(1L, REQUESTER.id(), ActivityType.REOPENED, "status", "RESOLVED", "OPEN");
    }

    @Test
    void requesterSeesOnlyTheMovesTheyMayMake() {
        existing(TicketStatus.RESOLVED);
        signedIn(REQUESTER);
        assertThat(ticketService.getTicketById(1L).allowedTransitions())
                .containsExactly(TicketStatus.OPEN, TicketStatus.CLOSED);

        existing(TicketStatus.IN_PROGRESS);
        assertThat(ticketService.getTicketById(1L).allowedTransitions()).containsExactly(TicketStatus.CANCELLED);
        assertThat(ticketService.getTicketById(1L).permissions().canAddInternalNote()).isFalse();
        assertThat(ticketService.getTicketById(1L).permissions().canViewInternal()).isFalse();
    }

    @Test
    void requesterCannotResolveTheirOwnTicket() {
        existing(TicketStatus.IN_PROGRESS);
        signedIn(REQUESTER);

        assertThatThrownBy(() -> ticketService.changeStatus(1L, new StatusChangeRequest("RESOLVED", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- workflow, concurrency, final states ----

    @Test
    void aMoveTheWorkflowForbidsIsAConflictEvenForAnAdmin() {
        existing(TicketStatus.OPEN);
        signedIn(ADMIN);

        assertThatThrownBy(() -> ticketService.changeStatus(1L, new StatusChangeRequest("RESOLVED", null)))
                .isInstanceOf(TicketWorkflowException.class)
                .hasMessageContaining("Allowed: [IN_PROGRESS, CANCELLED]");
    }

    @Test
    void staleVersionIsRejected() {
        existing(TicketStatus.OPEN);
        signedIn(ADMIN);

        assertThatThrownBy(() -> ticketService.updateTicket(1L,
                new TicketRequest("T", "D", null, "LOW", null, 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void finalTicketsCannotBeEdited() {
        existing(TicketStatus.CLOSED);
        signedIn(ADMIN);

        assertThatThrownBy(() -> ticketService.updateTicket(1L, details("x", null, "LOW")))
                .isInstanceOf(TicketWorkflowException.class)
                .hasMessageContaining("can no longer be edited");
    }

    // ---- SLA bookkeeping ----

    @Test
    void theFirstStaffStatusChangeCountsAsTheFirstResponse() {
        Ticket ticket = existing(TicketStatus.OPEN);
        signedIn(AGENT); // general queue: work access

        TicketResponse updated = ticketService.changeStatus(1L, new StatusChangeRequest("IN_PROGRESS", null));

        assertThat(ticket.getFirstResponseAt()).isEqualTo(NOW);
        // LOW response target 480 min from 08:00 -> due 16:00, responded at 12:00.
        assertThat(updated.sla().responseStatus()).isEqualTo(SlaStatus.MET);
    }

    @Test
    void waitingPausesTheResolutionClockAndResumingExtendsTheDeadline() {
        Ticket ticket = existing(TicketStatus.IN_PROGRESS);
        signedIn(ADMIN);
        Instant originalDue = ticket.getResolutionDueAt();

        TicketResponse waiting = ticketService.changeStatus(1L, new StatusChangeRequest("WAITING", null));
        assertThat(waiting.sla().resolutionStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(waiting.sla().paused()).isTrue();

        // Pretend the ticket has been waiting for two hours.
        ReflectionTestUtils.setField(ticket, "slaPausedAt", NOW.minus(Duration.ofHours(2)));
        ticketService.changeStatus(1L, new StatusChangeRequest("IN_PROGRESS", null));

        assertThat(ticket.getSlaPausedAt()).isNull();
        assertThat(ticket.getSlaPausedSeconds()).isEqualTo(7200);
        assertThat(ticket.getResolutionDueAt()).isEqualTo(originalDue.plus(Duration.ofHours(2)));
    }

    @Test
    void changingThePriorityRecomputesTheDeadlines() {
        Ticket ticket = existing(TicketStatus.IN_PROGRESS);
        signedIn(ADMIN);

        ticketService.updateTicket(1L, details("Old title", null, "CRITICAL"));

        assertThat(ticket.getResponseDueAt()).isEqualTo(EARLIER.plus(Duration.ofMinutes(15)));
        assertThat(ticket.getResolutionDueAt()).isEqualTo(EARLIER.plus(Duration.ofMinutes(240)));
        verify(activity).recordChange(1L, ADMIN.id(), ActivityType.PRIORITY_CHANGED, "priority", "LOW", "CRITICAL");
    }

    @Test
    void resolvingRecordsTheResolutionTimeInSeconds() {
        Ticket ticket = existing(TicketStatus.IN_PROGRESS);
        signedIn(ADMIN);

        ticketService.changeStatus(1L, new StatusChangeRequest("RESOLVED", null));

        assertThat(ticket.getResolvedAt()).isEqualTo(NOW);
        assertThat(ticket.getResolutionSeconds()).isEqualTo(Duration.between(EARLIER, NOW).getSeconds());
    }

    // ---- delete ----

    @Test
    void onlyAdministratorsDeleteAndFilesAreRemovedWithTheTicket() {
        signedIn(AGENT);
        assertThatThrownBy(() -> ticketService.deleteTicket(1L)).isInstanceOf(ForbiddenException.class);

        signedIn(ADMIN);
        when(ticketRepository.existsById(1L)).thenReturn(true);
        ticketService.deleteTicket(1L);
        verify(attachments).deleteFilesAfterCommit(eq(1L));
        verify(ticketRepository).deleteById(1L);

        assertThatThrownBy(() -> ticketService.deleteTicket(2L)).isInstanceOf(TicketNotFoundException.class);
    }
}
