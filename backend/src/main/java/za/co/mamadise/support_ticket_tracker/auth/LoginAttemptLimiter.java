package za.co.mamadise.support_ticket_tracker.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@code maxFailures} failed sign-ins for the same (normalised) username within
 * {@code window}, further attempts for that name are refused until the oldest failure leaves the window. A successful
 * sign-in clears the record.
 *
 * <p>Keyed by the submitted name, whether or not such an account exists, so the behaviour reveals nothing about which
 * accounts exist. In-memory and per instance: a restart clears it, and several instances would each count separately
 * (documented limitation; a shared store such as Redis would be the next step). Not keyed by IP address, because behind
 * the nginx proxy every request comes from the same address.
 */
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** True when the name currently has too many recent failures. */
    public boolean isBlocked(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            prune(recent);
            return recent.size() >= maxFailures;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(clock.instant());
        }
        if (failures.size() > 10_000) {
            // Bound memory under a spray of random names: drop entries with nothing recent.
            failures.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue());
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(window);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.removeFirst();
        }
    }
}
