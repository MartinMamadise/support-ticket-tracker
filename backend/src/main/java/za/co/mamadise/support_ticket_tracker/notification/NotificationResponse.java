package za.co.mamadise.support_ticket_tracker.notification;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An in-app notification of the signed-in account")
public record NotificationResponse(
        Long id,
        NotificationType type,
        @Schema(nullable = true, description = "Ticket the notification is about") Long ticketId,
        @Schema(example = "Ticket #42 was assigned to you: Printer on floor 2 is offline") String message,
        Instant createdAt,
        @Schema(nullable = true) Instant readAt,
        boolean read) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTicketId(), n.getMessage(), n.getCreatedAt(),
                n.getReadAt(), n.getReadAt() != null);
    }
}
