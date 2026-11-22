package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Duration;

/** Response and resolution targets for one priority, in minutes of calendar time. */
public record SlaTarget(int responseMinutes, int resolutionMinutes) {

    public SlaTarget {
        if (responseMinutes <= 0 || resolutionMinutes < responseMinutes) {
            throw new IllegalArgumentException("SLA targets must be positive and resolution >= response");
        }
    }

    public Duration response() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration resolution() {
        return Duration.ofMinutes(resolutionMinutes);
    }
}
