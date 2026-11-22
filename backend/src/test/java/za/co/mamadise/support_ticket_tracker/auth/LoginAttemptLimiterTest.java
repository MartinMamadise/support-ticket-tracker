package za.co.mamadise.support_ticket_tracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-21T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterTheLimitAndRecoversWhenTheWindowPasses() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.isBlocked("alice")).isFalse();
            limiter.recordFailure("alice");
        }
        assertThat(limiter.isBlocked("alice")).isTrue();
        assertThat(limiter.isBlocked("bob")).as("other names unaffected").isFalse();

        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThat(limiter.isBlocked("alice")).isFalse();
    }

    @Test
    void successClearsTheRecord() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, Duration.ofMinutes(15), new MutableClock());
        limiter.recordFailure("alice");
        limiter.recordSuccess("alice");
        limiter.recordFailure("alice");
        assertThat(limiter.isBlocked("alice")).isFalse();
    }
}
