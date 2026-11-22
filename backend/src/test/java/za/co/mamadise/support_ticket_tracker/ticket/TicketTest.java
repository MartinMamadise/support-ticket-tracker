package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Entity-level workflow and resolution-time rules. */
class TicketTest {

    private static final Instant T1 = Instant.parse("2026-11-21T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-21T11:00:00Z");
    private static final Instant T3 = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    void newTicketIsOpenAndUnresolved() {
        Ticket ticket = new Ticket();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void walkingTheHappyPathSetsResolutionTimeOnResolve() {
        Ticket ticket = new Ticket();

        ticket.changeStatus(TicketStatus.IN_PROGRESS, T1);
        assertThat(ticket.getResolvedAt()).isNull();

        ticket.changeStatus(TicketStatus.RESOLVED, T2);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isEqualTo(T2);

        ticket.changeStatus(TicketStatus.CLOSED, T3);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getResolvedAt()).isEqualTo(T2);
    }

    @Test
    void reopeningClearsResolutionTimeAndResolvingAgainRecordsANewOne() {
        Ticket ticket = new Ticket();
        ticket.changeStatus(TicketStatus.IN_PROGRESS, T1);
        ticket.changeStatus(TicketStatus.RESOLVED, T2);

        ticket.changeStatus(TicketStatus.OPEN, T3);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();

        ticket.changeStatus(TicketStatus.IN_PROGRESS, T3);
        ticket.changeStatus(TicketStatus.RESOLVED, T3);
        assertThat(ticket.getResolvedAt()).isEqualTo(T3);
    }

    @Test
    void cancellingNeverSetsResolutionTime() {
        Ticket ticket = new Ticket();
        ticket.changeStatus(TicketStatus.IN_PROGRESS, T1);

        ticket.changeStatus(TicketStatus.CANCELLED, T2);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void forbiddenTransitionThrowsAndLeavesTicketUnchanged() {
        Ticket ticket = new Ticket();

        assertThatThrownBy(() -> ticket.changeStatus(TicketStatus.RESOLVED, T1))
                .isInstanceOf(TicketWorkflowException.class)
                .hasMessageContaining("from OPEN to RESOLVED")
                .hasMessageContaining("IN_PROGRESS");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void finalStatusesCannotBeLeft() {
        Ticket cancelled = new Ticket();
        cancelled.changeStatus(TicketStatus.CANCELLED, T1);

        assertThatThrownBy(() -> cancelled.changeStatus(TicketStatus.OPEN, T2))
                .isInstanceOf(TicketWorkflowException.class)
                .hasMessageContaining("final");

        Ticket closed = new Ticket();
        closed.changeStatus(TicketStatus.IN_PROGRESS, T1);
        closed.changeStatus(TicketStatus.RESOLVED, T2);
        closed.changeStatus(TicketStatus.CLOSED, T3);

        assertThatThrownBy(() -> closed.changeStatus(TicketStatus.OPEN, T3))
                .isInstanceOf(TicketWorkflowException.class);
        assertThat(closed.getResolvedAt()).isEqualTo(T2);
    }

    @Test
    void settingTheCurrentStatusIsANoOp() {
        Ticket ticket = new Ticket();
        ticket.changeStatus(TicketStatus.IN_PROGRESS, T1);
        ticket.changeStatus(TicketStatus.RESOLVED, T2);

        ticket.changeStatus(TicketStatus.RESOLVED, T3);

        assertThat(ticket.getResolvedAt()).isEqualTo(T2);
    }
}
