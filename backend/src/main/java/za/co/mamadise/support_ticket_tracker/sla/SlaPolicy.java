package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;

/** SLA targets for one priority. One row per priority, seeded by V10, editable by administrators. */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private TicketPriority priority;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SlaPolicy() {
        // for JPA
    }

    public SlaPolicy(TicketPriority priority, int responseMinutes, int resolutionMinutes) {
        this.priority = priority;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public SlaTarget target() {
        return new SlaTarget(responseMinutes, resolutionMinutes);
    }

    public void change(SlaTarget target) {
        this.responseMinutes = target.responseMinutes();
        this.resolutionMinutes = target.resolutionMinutes();
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
