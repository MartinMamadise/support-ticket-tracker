package za.co.mamadise.support_ticket_tracker.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A persistent in-app notification for exactly one account. Table owned by Flyway (V11). */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_type", nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "ticket_id", updatable = false)
    private Long ticketId;

    @Column(nullable = false, length = 300, updatable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // for JPA
    }

    Notification(Long recipientId, NotificationType type, Long ticketId, String message, Instant createdAt) {
        this.recipientId = recipientId;
        this.type = type;
        this.ticketId = ticketId;
        this.message = message.length() <= 300 ? message : message.substring(0, 297) + "...";
        this.createdAt = createdAt;
    }

    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
