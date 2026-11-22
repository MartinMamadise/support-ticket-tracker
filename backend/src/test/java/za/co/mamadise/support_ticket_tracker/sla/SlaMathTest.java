package za.co.mamadise.support_ticket_tracker.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SlaMathTest {

    private static final Instant START = Instant.parse("2026-11-20T08:00:00Z");
    private static final SlaTarget HIGH = new SlaTarget(60, 480);

    @Test
    void deadlinesAreCalendarTimeFromTheStartPlusPausedTime() {
        assertThat(SlaMath.responseDue(START, HIGH)).isEqualTo(START.plus(Duration.ofHours(1)));
        assertThat(SlaMath.resolutionDue(START, HIGH, 0)).isEqualTo(START.plus(Duration.ofHours(8)));
        assertThat(SlaMath.resolutionDue(START, HIGH, 1800)).isEqualTo(START.plus(Duration.ofMinutes(510)));
        assertThat(SlaMath.resolutionDue(START, HIGH, -5)).as("negative pause ignored")
                .isEqualTo(START.plus(Duration.ofHours(8)));
    }

    @Test
    void warningStartsWhenAQuarterOfTheTargetIsLeft() {
        Instant due = START.plus(Duration.ofHours(8));
        assertThat(SlaMath.warningAt(due, Duration.ofHours(8))).isEqualTo(START.plus(Duration.ofHours(6)));
    }

    @Test
    void statusWhileRunning() {
        Instant due = START.plus(Duration.ofHours(8));
        Duration target = Duration.ofHours(8);
        assertThat(SlaMath.evaluate(due, target, null, null, START.plus(Duration.ofHours(1)))).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaMath.evaluate(due, target, null, null, START.plus(Duration.ofHours(6)))).isEqualTo(SlaStatus.AT_RISK);
        assertThat(SlaMath.evaluate(due, target, null, null, due)).as("exactly at the deadline is not yet breached")
                .isEqualTo(SlaStatus.AT_RISK);
        assertThat(SlaMath.evaluate(due, target, null, null, due.plusSeconds(1))).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void statusOnceCompleted() {
        Instant due = START.plus(Duration.ofHours(8));
        assertThat(SlaMath.evaluate(due, null, due, null, due.plusSeconds(999))).isEqualTo(SlaStatus.MET);
        assertThat(SlaMath.evaluate(due, null, due.plusSeconds(1), null, due)).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void pausedClocksFreezeAtThePauseTime() {
        Instant due = START.plus(Duration.ofHours(8));
        Instant pausedAt = START.plus(Duration.ofHours(2));
        assertThat(SlaMath.evaluate(due, Duration.ofHours(8), null, pausedAt, due.plus(Duration.ofDays(3))))
                .isEqualTo(SlaStatus.PAUSED);
        assertThat(SlaMath.remainingSeconds(due, null, pausedAt, due.plus(Duration.ofDays(3))))
                .isEqualTo(Duration.ofHours(6).getSeconds());
        assertThat(SlaMath.evaluate(due, Duration.ofHours(8), null, due.plusSeconds(10), due.plusSeconds(20)))
                .as("paused only after the deadline had already passed").isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void noDeadlineMeansNotApplicable() {
        assertThat(SlaMath.evaluate(null, null, null, null, START)).isEqualTo(SlaStatus.NOT_APPLICABLE);
        assertThat(SlaMath.remainingSeconds(null, null, null, START)).isNull();
    }

    @Test
    void remainingIsNegativeWhenOverdueAndNullWhenDone() {
        Instant due = START.plus(Duration.ofHours(1));
        assertThat(SlaMath.remainingSeconds(due, null, null, due.plusSeconds(90))).isEqualTo(-90);
        assertThat(SlaMath.remainingSeconds(due, due, null, due.plusSeconds(90))).isNull();
    }

    @Test
    void targetsMustBePositiveAndResolutionNotShorterThanResponse() {
        assertThatThrownBy(() -> new SlaTarget(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlaTarget(60, 30)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultTargetsCoverEveryPriority() {
        for (var priority : za.co.mamadise.support_ticket_tracker.ticket.TicketPriority.values()) {
            assertThat(SlaService.defaultTarget(priority)).as(priority.name()).isNotNull();
        }
        assertThat(SlaService.defaultTarget(za.co.mamadise.support_ticket_tracker.ticket.TicketPriority.CRITICAL))
                .isEqualTo(new SlaTarget(15, 240));
    }
}
