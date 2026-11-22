package za.co.mamadise.support_ticket_tracker.comment;

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

import za.co.mamadise.support_ticket_tracker.user.AppUser;

/**
 * A public comment or an internal note on a ticket ({@code internal = true}). Internal notes are staff-only; the
 * filtering happens in the repository queries, never by hiding them in the client. Table owned by Flyway (V9).
 */
@Entity
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private AppUser author;

    @Column(nullable = false, length = 5000)
    private String body;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    @Column(nullable = false)
    private boolean edited;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketComment() {
        // for JPA
    }

    TicketComment(Long ticketId, AppUser author, String body, boolean internal, Instant now) {
        this.ticketId = ticketId;
        this.author = author;
        this.body = body;
        this.internal = internal;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void edit(String newBody, Instant now) {
        if (!newBody.equals(body)) {
            this.body = newBody;
            this.edited = true;
            this.updatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean isEdited() {
        return edited;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
