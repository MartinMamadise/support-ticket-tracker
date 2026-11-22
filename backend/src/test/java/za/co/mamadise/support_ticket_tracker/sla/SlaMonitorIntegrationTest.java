package za.co.mamadise.support_ticket_tracker.sla;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import za.co.mamadise.support_ticket_tracker.TestDatabase;
import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.notification.NotificationRepository;
import za.co.mamadise.support_ticket_tracker.notification.NotificationService;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.Role;

/** The SLA monitor (disabled as a scheduled job in tests) called directly against the real database. */
@SpringBootTest
@Import(TestDatabase.class)
class SlaMonitorIntegrationTest {

    @Autowired
    private TestDatabase database;
    @Autowired
    private TicketRepository tickets;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private ActivityService activityService;
    @Autowired
    private TransactionTemplate tx;

    private AppUser agent;

    @BeforeEach
    void data() {
        database.reset();
        agent = database.user("sla.agent", Role.SUPPORT_AGENT);
    }

    private Ticket overdueTicket(Instant created) {
        Ticket t = new Ticket();
        t.setTitle("Overdue");
        t.setDescription("D");
        t.setPriority(TicketPriority.CRITICAL);
        t.setCreatedAt(created);
        t.setAssignee(agent);
        t.startSla(new SlaTarget(15, 240), created);
        return tickets.saveAndFlush(t);
    }

    /** All Spring tests share one H2 database: leave it empty for the next class. */
    @AfterEach
    void resetAfter() {
        database.reset();
    }

    @Test
    void breachesAreNotifiedExactlyOnce() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        overdueTicket(now.minus(Duration.ofHours(5)));
        SlaMonitor monitor = new SlaMonitor(tickets, notificationService, activityService,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));

        tx.executeWithoutResult(s -> monitor.check());
        long afterFirst = notifications.count();
        tx.executeWithoutResult(s -> monitor.check());

        // response breach + resolution breach, both to the assignee, and nothing new on the second run.
        assertThat(afterFirst).isEqualTo(2);
        assertThat(notifications.count()).isEqualTo(afterFirst);
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(agent.getId())).isEqualTo(2);
    }

    @Test
    void approachingDeadlinesGetOneWarning() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        // CRITICAL: 240 min resolution, warning from minute 180. Created 200 minutes ago, first response given.
        Ticket t = overdueTicket(now.minus(Duration.ofMinutes(200)));
        ReflectionTestUtils.setField(t, "firstResponseAt", now.minus(Duration.ofMinutes(190)));
        tickets.saveAndFlush(t);
        SlaMonitor monitor = new SlaMonitor(tickets, notificationService, activityService,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));

        tx.executeWithoutResult(s -> monitor.check());
        tx.executeWithoutResult(s -> monitor.check());

        assertThat(notifications.count()).isEqualTo(1);
    }
}
