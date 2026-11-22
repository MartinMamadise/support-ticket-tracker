package za.co.mamadise.support_ticket_tracker.notification;

/**
 * Extension point for additional delivery channels (e-mail, chat). Every stored notification is passed to each channel
 * bean after it is saved. None is registered today: in-app notifications are the only channel, and no external service
 * is needed. An implementation must not throw for delivery failures (log and continue) and must respect the same
 * content rules (no internal-note text for requesters; the message never contains comment text anyway).
 */
public interface NotificationChannel {

    void deliver(Notification notification);
}
