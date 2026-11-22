package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Duration;
import java.time.Instant;

/**
 * The SLA arithmetic, kept free of persistence and framework types so it can be tested in isolation.
 *
 * <p>Assumptions (documented in docs/SLA.md):
 * <ul>
 * <li>Calendar time, 24/7. No business hours or holidays.</li>
 * <li>Response deadline = creation + response target. It is never paused or restarted.</li>
 * <li>Resolution deadline = start + resolution target + total time spent WAITING. The start is the creation time,
 * or the time of the last reopen (a reopened ticket gets a fresh resolution target).</li>
 * <li>A target is at risk once at most {@value #AT_RISK_PERCENT}% of it is left.</li>
 * <li>A deadline is breached when the current (or completion) time is strictly after it.</li>
 * </ul>
 */
public final class SlaMath {

    public static final int AT_RISK_PERCENT = 25;

    private SlaMath() {
    }

    public static Instant responseDue(Instant createdAt, SlaTarget target) {
        return createdAt.plus(target.response());
    }

    public static Instant resolutionDue(Instant startedAt, SlaTarget target, long pausedSeconds) {
        return startedAt.plus(target.resolution()).plusSeconds(Math.max(0, pausedSeconds));
    }

    /** The moment the target becomes at risk: when {@value #AT_RISK_PERCENT}% of it is left. */
    public static Instant warningAt(Instant due, Duration target) {
        return due.minus(target.multipliedBy(AT_RISK_PERCENT).dividedBy(100));
    }

    /**
     * Status of one target.
     *
     * @param due         the deadline, or null when no SLA applies
     * @param target      the full target duration (for the at-risk threshold)
     * @param completedAt when the target was met (first response / resolution), or null while still running
     * @param pausedAt    when the clock was paused (WAITING), or null while running
     * @param now         the current time
     */
    public static SlaStatus evaluate(Instant due, Duration target, Instant completedAt, Instant pausedAt, Instant now) {
        if (due == null) {
            return SlaStatus.NOT_APPLICABLE;
        }
        if (completedAt != null) {
            return completedAt.isAfter(due) ? SlaStatus.BREACHED : SlaStatus.MET;
        }
        Instant reference = pausedAt != null ? pausedAt : now;
        if (reference.isAfter(due)) {
            return SlaStatus.BREACHED;
        }
        if (pausedAt != null) {
            return SlaStatus.PAUSED;
        }
        if (target != null && !now.isBefore(warningAt(due, target))) {
            return SlaStatus.AT_RISK;
        }
        return SlaStatus.ON_TRACK;
    }

    /**
     * Seconds left until the deadline (negative when overdue), measured at {@code pausedAt} while paused, or null
     * when there is no deadline or the target has already been met or missed for good.
     */
    public static Long remainingSeconds(Instant due, Instant completedAt, Instant pausedAt, Instant now) {
        if (due == null || completedAt != null) {
            return null;
        }
        Instant reference = pausedAt != null ? pausedAt : now;
        return Duration.between(reference, due).getSeconds();
    }
}
