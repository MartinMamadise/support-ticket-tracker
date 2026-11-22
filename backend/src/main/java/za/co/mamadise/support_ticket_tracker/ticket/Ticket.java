package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Duration;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import za.co.mamadise.support_ticket_tracker.sla.SlaMath;
import za.co.mamadise.support_ticket_tracker.sla.SlaTarget;
import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.user.AppUser;

/**
 * Persistent ticket. This class is internal to the backend: the API only ever exposes {@link TicketResponse} and
 * accepts request records.
 *
 * The table is created and changed only by the Flyway migrations in {@code db/migration}; Hibernate validates that
 * the mapping below still matches it. Any change to a column here needs a new migration.
 * The explicit JDBC types make the mapping identical on MySQL and H2: enums are VARCHAR (not MySQL's native ENUM)
 * and instants are TIMESTAMP/DATETIME (not TIMESTAMP WITH TIME ZONE), stored as UTC via {@code hibernate.jdbc.time_zone}.
 *
 * Requester, assignee and team are mapped twice: as lazy associations (used when changing them) and as read-only id
 * columns (used by queries, so filters never need a join). The {@code get...Id()} getters read the association, so
 * they are correct even before a newly changed ticket is reloaded.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketCategory category = TicketCategory.OTHER;

    /** Who raised the ticket. Null only for tickets created before accounts existed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private AppUser requester;

    @Column(name = "requester_id", insertable = false, updatable = false)
    private Long requesterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Column(name = "assignee_id", insertable = false, updatable = false)
    private Long assigneeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "team_id", insertable = false, updatable = false)
    private Long teamId;

    /** All timestamps are UTC instants. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false)
    private Instant updatedAt;

    /** When the ticket first reached RESOLVED or CLOSED; null while it is active or cancelled. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column
    private Instant resolvedAt;

    /** Calendar seconds from creation to {@code resolvedAt} (for SQL averages); null when not resolved. */
    @Column(name = "resolution_seconds")
    private Long resolutionSeconds;

    // --- SLA state (see sla.SlaMath for the rules). All null for tickets created before SLAs existed. ---

    /** Start of the resolution clock: creation, or the last reopen. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "sla_started_at")
    private Instant slaStartedAt;

    /** First public reply or status change by support staff. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "resolution_warning_at")
    private Instant resolutionWarningAt;

    /** Set while WAITING: the resolution clock is stopped. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "sla_paused_at")
    private Instant slaPausedAt;

    @Column(name = "sla_paused_seconds", nullable = false)
    private long slaPausedSeconds;

    @Column(name = "response_breach_notified", nullable = false)
    private boolean responseBreachNotified;

    @Column(name = "resolution_warning_notified", nullable = false)
    private boolean resolutionWarningNotified;

    @Column(name = "resolution_breach_notified", nullable = false)
    private boolean resolutionBreachNotified;

    /**
     * Read-only sort keys computed by the database. Status and priority are stored as names, which
     * would sort alphabetically; these ranks give the meaningful order
     * LOW &lt; MEDIUM &lt; HIGH &lt; CRITICAL and OPEN &lt; IN_PROGRESS &lt; WAITING &lt; RESOLVED &lt; CLOSED &lt; CANCELLED.
     * They are never written and are only used through {@link TicketSortField}.
     */
    @Formula("CASE WHEN priority = 'LOW' THEN 1 WHEN priority = 'MEDIUM' THEN 2 WHEN priority = 'HIGH' THEN 3 "
            + "WHEN priority = 'CRITICAL' THEN 4 ELSE 0 END")
    private int priorityRank;

    @Formula("CASE WHEN status = 'OPEN' THEN 1 WHEN status = 'IN_PROGRESS' THEN 2 WHEN status = 'WAITING' THEN 3 "
            + "WHEN status = 'RESOLVED' THEN 4 WHEN status = 'CLOSED' THEN 5 WHEN status = 'CANCELLED' THEN 6 ELSE 0 END")
    private int statusRank;

    /** Optimistic-locking counter, incremented by Hibernate on every update. */
    @Version
    @Column(nullable = false)
    private Long version;

    public Ticket() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        // Only fill in what is missing: the service sets createdAt from the application clock (so the SLA start and
        // the creation time agree); tests and data imports may persist tickets with specific times.
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * The only way to change a ticket's status. Enforces the workflow in {@link TicketStatus}
     * (throws {@link TicketWorkflowException} for a forbidden move; "changing" to the current
     * status is a no-op) and keeps {@code resolvedAt} consistent with the new status:
     * the first move into RESOLVED records {@code now}, RESOLVED to CLOSED keeps the original
     * time, and leaving a resolution state (reopening) clears it. Who may make which move is checked by the service
     * ({@link TicketAccessPolicy}); SLA bookkeeping for the move is done by {@code sla.SlaService}.
     */
    public void changeStatus(TicketStatus newStatus, Instant now) {
        if (newStatus == this.status) {
            return;
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw TicketWorkflowException.invalidTransition(this.status, newStatus);
        }
        if (newStatus.isResolution()) {
            if (this.resolvedAt == null) {
                this.resolvedAt = now;
                this.resolutionSeconds = createdAt == null ? null
                        : Math.max(0, Duration.between(createdAt, now).getSeconds());
            }
        } else {
            this.resolvedAt = null;
            this.resolutionSeconds = null;
        }
        this.status = newStatus;
    }

    // --- SLA operations, called by sla.SlaService ---

    /** Starts the SLA clocks at creation. */
    public void startSla(SlaTarget target, Instant now) {
        this.slaStartedAt = now;
        this.slaPausedSeconds = 0;
        this.slaPausedAt = null;
        recalculateSla(target);
    }

    /** Recomputes the deadlines from the stored state, e.g. after a priority change. No-op without an SLA. */
    public void recalculateSla(SlaTarget target) {
        if (slaStartedAt == null) {
            return;
        }
        if (createdAt != null) {
            this.responseDueAt = SlaMath.responseDue(createdAt, target);
        }
        this.resolutionDueAt = SlaMath.resolutionDue(slaStartedAt, target, slaPausedSeconds);
        this.resolutionWarningAt = SlaMath.warningAt(resolutionDueAt, target.resolution());
    }

    /** WAITING: stop the resolution clock. */
    public void pauseSla(Instant now) {
        if (slaStartedAt != null && slaPausedAt == null) {
            this.slaPausedAt = now;
        }
    }

    /** Leaving WAITING: the time spent waiting is added to the resolution deadline. */
    public void resumeSla(SlaTarget target, Instant now) {
        if (slaPausedAt != null) {
            this.slaPausedSeconds += Math.max(0, Duration.between(slaPausedAt, now).getSeconds());
            this.slaPausedAt = null;
            recalculateSla(target);
        }
    }

    /** Reopen: a fresh resolution target from now. The response SLA is not affected. Starts an SLA for legacy tickets. */
    public void restartResolutionSla(SlaTarget target, Instant now) {
        this.slaStartedAt = now;
        this.slaPausedSeconds = 0;
        this.slaPausedAt = null;
        this.resolutionWarningNotified = false;
        this.resolutionBreachNotified = false;
        recalculateSla(target);
    }

    /** Records the first response by support staff (later responses do not change it). */
    public boolean recordFirstResponse(Instant now) {
        if (firstResponseAt == null) {
            this.firstResponseAt = now;
            return true;
        }
        return false;
    }

    public void markResponseBreachNotified() {
        this.responseBreachNotified = true;
    }

    public void markResolutionWarningNotified() {
        this.resolutionWarningNotified = true;
    }

    public void markResolutionBreachNotified() {
        this.resolutionBreachNotified = true;
    }

    // --- accessors ---

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public void setCategory(TicketCategory category) {
        this.category = category == null ? TicketCategory.OTHER : category;
    }

    public AppUser getRequester() {
        return requester;
    }

    public void setRequester(AppUser requester) {
        this.requester = requester;
    }

    public Long getRequesterId() {
        return requester == null ? null : requester.getId();
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public void setAssignee(AppUser assignee) {
        this.assignee = assignee;
    }

    public Long getAssigneeId() {
        return assignee == null ? null : assignee.getId();
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Long getTeamId() {
        return team == null ? null : team.getId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Only for the service (application clock) and imports; must be called before the ticket is first saved. */
    public void setCreatedAt(Instant createdAt) {
        if (this.id == null) {
            this.createdAt = createdAt;
        }
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Long getResolutionSeconds() {
        return resolutionSeconds;
    }

    public Instant getSlaStartedAt() {
        return slaStartedAt;
    }

    public Instant getFirstResponseAt() {
        return firstResponseAt;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public Instant getResolutionDueAt() {
        return resolutionDueAt;
    }

    public Instant getResolutionWarningAt() {
        return resolutionWarningAt;
    }

    public Instant getSlaPausedAt() {
        return slaPausedAt;
    }

    public long getSlaPausedSeconds() {
        return slaPausedSeconds;
    }

    public boolean isResponseBreachNotified() {
        return responseBreachNotified;
    }

    public boolean isResolutionWarningNotified() {
        return resolutionWarningNotified;
    }

    public boolean isResolutionBreachNotified() {
        return resolutionBreachNotified;
    }

    public Long getVersion() {
        return version;
    }
}
