package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Pins down the complete workflow table so any change to it is deliberate. */
class TicketStatusTest {

    private static Set<TicketStatus> allowed(TicketStatus... statuses) {
        return statuses.length == 0 ? EnumSet.noneOf(TicketStatus.class) : EnumSet.of(statuses[0], statuses);
    }

    @Test
    void workflowTableIsExactlyAsDocumented() {
        assertThat(TicketStatus.OPEN.allowedTransitions())
                .isEqualTo(allowed(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        assertThat(TicketStatus.IN_PROGRESS.allowedTransitions())
                .isEqualTo(allowed(TicketStatus.OPEN, TicketStatus.WAITING, TicketStatus.RESOLVED, TicketStatus.CANCELLED));
        assertThat(TicketStatus.WAITING.allowedTransitions())
                .isEqualTo(allowed(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CANCELLED));
        assertThat(TicketStatus.RESOLVED.allowedTransitions())
                .isEqualTo(allowed(TicketStatus.OPEN, TicketStatus.CLOSED));
        assertThat(TicketStatus.CLOSED.allowedTransitions()).isEmpty();
        assertThat(TicketStatus.CANCELLED.allowedTransitions()).isEmpty();
    }

    @Test
    void openCannotJumpStraightToResolvedOrClosed() {
        assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED)).isFalse();
        assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED)).isFalse();
    }

    @Test
    void noStatusTransitionsToItself() {
        for (TicketStatus status : TicketStatus.values()) {
            assertThat(status.canTransitionTo(status)).as(status.name()).isFalse();
        }
    }

    @Test
    void onlyClosedAndCancelledAreFinal() {
        assertThat(TicketStatus.CLOSED.isFinal()).isTrue();
        assertThat(TicketStatus.CANCELLED.isFinal()).isTrue();
        assertThat(TicketStatus.OPEN.isFinal()).isFalse();
        assertThat(TicketStatus.IN_PROGRESS.isFinal()).isFalse();
        assertThat(TicketStatus.RESOLVED.isFinal()).isFalse();
        assertThat(TicketStatus.WAITING.isFinal()).isFalse();
    }

    @Test
    void activeStatusesAreTheOnesStillBeingWorkedOn() {
        assertThat(TicketStatus.activeStatuses())
                .containsExactlyInAnyOrder(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING);
        assertThat(TicketStatus.RESOLVED.isActive()).isFalse();
    }

    @Test
    void onlyResolvedAndClosedCarryAResolution() {
        assertThat(TicketStatus.RESOLVED.isResolution()).isTrue();
        assertThat(TicketStatus.CLOSED.isResolution()).isTrue();
        assertThat(TicketStatus.CANCELLED.isResolution()).isFalse();
        assertThat(TicketStatus.OPEN.isResolution()).isFalse();
    }
}
