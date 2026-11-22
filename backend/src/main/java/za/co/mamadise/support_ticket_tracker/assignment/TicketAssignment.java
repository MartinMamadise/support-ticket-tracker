package za.co.mamadise.support_ticket_tracker.assignment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.user.AppUser;

/** One assignment change of a ticket: who and which team before and after, who made the change, when. Immutable. */
@Entity
@Table(name = "ticket_assignments")
public class TicketAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_assignee_id", updatable = false)
    private AppUser previousAssignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_assignee_id", updatable = false)
    private AppUser newAssignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_team_id", updatable = false)
    private Team previousTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_team_id", updatable = false)
    private Team newTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private AppUser actor;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketAssignment() {
        // for JPA
    }

    TicketAssignment(Long ticketId, AppUser previousAssignee, AppUser newAssignee, Team previousTeam, Team newTeam,
            AppUser actor, Instant createdAt) {
        this.ticketId = ticketId;
        this.previousAssignee = previousAssignee;
        this.newAssignee = newAssignee;
        this.previousTeam = previousTeam;
        this.newTeam = newTeam;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public AppUser getPreviousAssignee() {
        return previousAssignee;
    }

    public AppUser getNewAssignee() {
        return newAssignee;
    }

    public Team getPreviousTeam() {
        return previousTeam;
    }

    public Team getNewTeam() {
        return newTeam;
    }

    public AppUser getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
