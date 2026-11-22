package za.co.mamadise.support_ticket_tracker.notification;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.NotFoundException;
import za.co.mamadise.support_ticket_tracker.ticket.PageResponse;

/**
 * Stores notifications and serves the signed-in account's own notifications. Messages are built by the calling
 * services from ticket number, title and the event; they never include comment or note text.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ObjectProvider<NotificationChannel> channels;

    public NotificationService(NotificationRepository notifications, CurrentUser currentUser, Clock clock,
            ObjectProvider<NotificationChannel> channels) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
        this.channels = channels;
    }

    /**
     * Notifies each distinct recipient except {@code actorId} (nobody is notified about their own action).
     * Joins the caller's transaction, so notifications only exist for committed changes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Collection<Long> recipientIds, Long actorId, NotificationType type, Long ticketId, String message) {
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : recipientIds) {
            if (id != null && !Objects.equals(id, actorId)) {
                distinct.add(id);
            }
        }
        for (Long recipient : distinct) {
            Notification saved = notifications.save(new Notification(recipient, type, ticketId, message, clock.instant()));
            for (NotificationChannel channel : channels.orderedStream().toList()) {
                try {
                    channel.deliver(saved);
                } catch (RuntimeException ex) {
                    log.warn("Notification channel {} failed for notification {}", channel.getClass().getSimpleName(),
                            saved.getId());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size, boolean unreadOnly) {
        Viewer viewer = currentUser.require();
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNull(viewer.id(), request)
                : notifications.findByRecipientId(viewer.id(), request), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByRecipientIdAndReadAtIsNull(currentUser.require().id());
    }

    /** Someone else's notification id gives the same 404 as a missing one. */
    @Transactional
    public NotificationResponse markRead(Long id) {
        Viewer viewer = currentUser.require();
        Notification notification = notifications.findByIdAndRecipientId(id, viewer.id())
                .orElseThrow(() -> new NotFoundException("Notification " + id + " not found"));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(currentUser.require().id(), clock.instant());
    }
}
