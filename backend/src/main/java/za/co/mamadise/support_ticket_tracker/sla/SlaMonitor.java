package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.notification.NotificationService;
import za.co.mamadise.support_ticket_tracker.notification.NotificationType;
import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketRepository;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;
import za.co.mamadise.support_ticket_tracker.user.AppUser;

/**
 * Periodically notifies about SLA deadlines that are approaching or were missed. Each event is sent once per ticket
 * (flags on the ticket; reset when a reopen restarts the clock). Recipients: the assigned agent, otherwise the active
 * members of the ticket's team; unassigned tickets without a team only show up on the dashboard. Requesters are not
 * notified about SLA events. Disabled with {@code app.sla.monitor.enabled=false} (tests do this).
 */
@Component
@ConditionalOnProperty(prefix = "app.sla.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);
    private static final int BATCH = 200;

    private final TicketRepository tickets;
    private final NotificationService notifications;
    private final ActivityService activity;
    private final Clock clock;

    public SlaMonitor(TicketRepository tickets, NotificationService notifications, ActivityService activity, Clock clock) {
        this.tickets = tickets;
        this.notifications = notifications;
        this.activity = activity;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.sla.monitor.interval-ms:60000}", initialDelayString = "${app.sla.monitor.initial-delay-ms:30000}")
    @Transactional
    public void check() {
        Instant now = clock.instant();
        int sent = 0;
        var active = TicketStatus.activeStatuses();
        for (Ticket t : tickets.findUnnotifiedResponseBreaches(active, now, PageRequest.ofSize(BATCH))) {
            t.markResponseBreachNotified();
            activity.record(t.getId(), null, ActivityType.SLA_RESPONSE_BREACHED);
            notifications.notify(recipients(t), null, NotificationType.SLA_BREACHED, t.getId(),
                    "Response SLA breached on ticket #" + t.getId() + ": " + t.getTitle());
            sent++;
        }
        for (Ticket t : tickets.findUnnotifiedResolutionWarnings(active, now, PageRequest.ofSize(BATCH))) {
            t.markResolutionWarningNotified();
            notifications.notify(recipients(t), null, NotificationType.SLA_WARNING, t.getId(),
                    "Resolution deadline approaching on ticket #" + t.getId() + ": " + t.getTitle());
            sent++;
        }
        for (Ticket t : tickets.findUnnotifiedResolutionBreaches(active, now, PageRequest.ofSize(BATCH))) {
            t.markResolutionBreachNotified();
            t.markResolutionWarningNotified(); // no late "approaching" after the breach
            activity.record(t.getId(), null, ActivityType.SLA_RESOLUTION_BREACHED);
            notifications.notify(recipients(t), null, NotificationType.SLA_BREACHED, t.getId(),
                    "Resolution SLA breached on ticket #" + t.getId() + ": " + t.getTitle());
            sent++;
        }
        if (sent > 0) {
            log.info("SLA monitor processed {} event(s)", sent);
        }
    }

    private static List<Long> recipients(Ticket t) {
        List<Long> ids = new ArrayList<>();
        AppUser assignee = t.getAssignee();
        if (assignee != null && assignee.isEnabled()) {
            ids.add(assignee.getId());
            return ids;
        }
        Team team = t.getTeam();
        if (team != null) {
            team.getMembers().stream().filter(AppUser::isEnabled).map(AppUser::getId).forEach(ids::add);
        }
        return ids;
    }
}
