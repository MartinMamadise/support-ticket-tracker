package za.co.mamadise.support_ticket_tracker.activity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import za.co.mamadise.support_ticket_tracker.user.AppUser;

/**
 * One entry of a ticket's history. Immutable once written. Old/new values hold short, non-sensitive representations
 * (enum names, account names, team names): never comment text, passwords or tokens. Table owned by Flyway (V9).
 */
@Entity
@Table(name = "ticket_activity")
public class TicketActivity {

    /** What the entry is about. */
    public enum EntityType { TICKET, COMMENT, ATTACHMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    /** Null for changes made by the system (SLA monitor). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "activity_type", nullable = false, length = 40, updatable = false)
    private ActivityType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entity_type", nullable = false, length = 20, updatable = false)
    private EntityType entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(name = "field_name", length = 40, updatable = false)
    private String fieldName;

    @Column(name = "old_value", length = 255, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = 255, updatable = false)
    private String newValue;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketActivity() {
        // for JPA
    }

    TicketActivity(Long ticketId, AppUser actor, ActivityType type, EntityType entityType, Long entityId,
            String fieldName, String oldValue, String newValue, boolean internal, Instant createdAt) {
        this.ticketId = ticketId;
        this.actor = actor;
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldName = fieldName;
        this.oldValue = truncate(oldValue);
        this.newValue = truncate(newValue);
        this.internal = internal;
        this.createdAt = createdAt;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 252) + "...";
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public AppUser getActor() {
        return actor;
    }

    public ActivityType getType() {
        return type;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
