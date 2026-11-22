package za.co.mamadise.support_ticket_tracker.attachment;

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

/** Metadata of a file attached to a ticket. The bytes are in {@link AttachmentStorage} under {@code storageKey}. */
@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false, updatable = false)
    private AppUser uploader;

    @Column(nullable = false, length = 255, updatable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 64, updatable = false, unique = true)
    private String storageKey;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketAttachment() {
        // for JPA
    }

    TicketAttachment(Long ticketId, AppUser uploader, String filename, String contentType, long sizeBytes,
            String storageKey, String sha256, Instant createdAt) {
        this.ticketId = ticketId;
        this.uploader = uploader;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.sha256 = sha256;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public AppUser getUploader() {
        return uploader;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
