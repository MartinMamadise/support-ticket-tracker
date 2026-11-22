package za.co.mamadise.support_ticket_tracker.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.activity.TicketActivity.EntityType;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.BadRequestException;
import za.co.mamadise.support_ticket_tracker.error.ConflictException;
import za.co.mamadise.support_ticket_tracker.error.ForbiddenException;
import za.co.mamadise.support_ticket_tracker.error.NotFoundException;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketAccessPolicy;
import za.co.mamadise.support_ticket_tracker.ticket.TicketLookup;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * Upload, list, download and delete ticket attachments.
 * <ul>
 * <li>Access follows the ticket: whoever can see the ticket can list and download its attachments; whoever can
 * comment can upload. Deleting: the uploader (while the ticket is not final) or an administrator.</li>
 * <li>Allowed: PNG, JPEG, PDF, plain text (.txt, .log). The extension selects the claimed type and the bytes must
 * match it ({@link FileTypeDetector}); the client's Content-Type is ignored. Empty files are refused.</li>
 * <li>Size limit {@code app.attachments.max-bytes} (default 5 MB; the multipart limit is set slightly higher so the
 * service can answer with its own message), at most {@code app.attachments.max-per-ticket} files per ticket.</li>
 * <li>The original name is sanitised for display only; files are stored under a random key.</li>
 * </ul>
 */
@Service
@Transactional
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final TicketAttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final TicketLookup lookup;
    private final AppUserRepository users;
    private final ActivityService activity;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final long maxBytes;
    private final int maxPerTicket;

    public AttachmentService(TicketAttachmentRepository attachments, AttachmentStorage storage, TicketLookup lookup,
            AppUserRepository users, ActivityService activity, CurrentUser currentUser, Clock clock,
            @Value("${app.attachments.max-bytes:5242880}") long maxBytes,
            @Value("${app.attachments.max-per-ticket:20}") int maxPerTicket) {
        this.attachments = attachments;
        this.storage = storage;
        this.lookup = lookup;
        this.users = users;
        this.activity = activity;
        this.currentUser = currentUser;
        this.clock = clock;
        this.maxBytes = maxBytes;
        this.maxPerTicket = maxPerTicket;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Long ticketId) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        return attachments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream()
                .map(a -> AttachmentResponse.from(a, canDelete(viewer, ticket, a)))
                .toList();
    }

    public AttachmentResponse upload(Long ticketId, MultipartFile file) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("Ticket is " + ticket.getStatus() + " and no longer accepts attachments");
        }
        if (!TicketAccessPolicy.canUpload(viewer, ticket)) {
            throw new ForbiddenException("You are not allowed to add attachments to this ticket");
        }
        if (attachments.countByTicketId(ticketId) >= maxPerTicket) {
            throw new ConflictException("A ticket can have at most " + maxPerTicket + " attachments");
        }
        if (file == null || file.isEmpty()) {
            throw BadRequestException.field("file", "The file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw BadRequestException.field("file", "The file is larger than " + (maxBytes / (1024 * 1024)) + " MB");
        }
        FileTypeDetector.AllowedType type = FileTypeDetector.fromExtension(file.getOriginalFilename())
                .orElseThrow(() -> BadRequestException.field("file", "Allowed file types: PNG, JPG, JPEG, PDF, TXT, LOG"));
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw BadRequestException.field("file", "The file could not be read");
        }
        if (!FileTypeDetector.contentMatches(type, content)) {
            log.warn("Rejected upload to ticket {}: content does not match the claimed type {}", ticketId, type);
            throw BadRequestException.field("file", "The file content does not match its type (" + type + ")");
        }
        String extension = type == FileTypeDetector.AllowedType.JPEG ? ".jpg"
                : "." + type.name().toLowerCase(java.util.Locale.ROOT).replace("text", "txt");
        String filename = FilenameSanitizer.sanitize(file.getOriginalFilename(), "attachment" + extension);
        String key = UUID.randomUUID().toString().replace("-", "");
        try {
            storage.store(key, content);
        } catch (IOException e) {
            log.error("Could not store attachment for ticket {}", ticketId, e);
            throw new IllegalStateException("Attachment storage failed");
        }
        // If the database insert fails later, remove the orphan file.
        deleteFilesOnRollback(List.of(key));
        Instant now = clock.instant();
        TicketAttachment saved = attachments.saveAndFlush(new TicketAttachment(ticketId,
                users.getReferenceById(viewer.id()), filename, type.contentType(), content.length, key, sha256(content), now));
        activity.record(ticketId, viewer.id(), ActivityType.ATTACHMENT_ADDED, EntityType.ATTACHMENT, saved.getId(),
                "attachment", null, filename, false);
        return AttachmentResponse.from(saved, true);
    }

    /** Opens the stored file after the same visibility check as the ticket itself. */
    @Transactional(readOnly = true)
    public AttachmentDownload download(Long ticketId, Long attachmentId) {
        Viewer viewer = currentUser.require();
        lookup.requireVisible(ticketId, viewer);
        TicketAttachment attachment = find(ticketId, attachmentId);
        try {
            InputStream in = storage.open(attachment.getStorageKey());
            return new AttachmentDownload(attachment.getFilename(), attachment.getContentType(),
                    attachment.getSizeBytes(), in);
        } catch (IOException e) {
            log.error("Stored file missing for attachment {}", attachmentId);
            throw new NotFoundException("Attachment " + attachmentId + " not found");
        }
    }

    public void delete(Long ticketId, Long attachmentId) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(ticketId, viewer);
        TicketAttachment attachment = find(ticketId, attachmentId);
        if (!canDelete(viewer, ticket, attachment)) {
            throw new ForbiddenException("You are not allowed to delete this attachment");
        }
        attachments.delete(attachment);
        activity.record(ticketId, viewer.id(), ActivityType.ATTACHMENT_REMOVED, EntityType.ATTACHMENT, attachmentId,
                "attachment", attachment.getFilename(), null, false);
        deleteFilesAfterCommit(List.of(attachment.getStorageKey()));
    }

    /** Called when a ticket is deleted: its rows go with the ticket (FK cascade), its files after the commit. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void deleteFilesAfterCommit(Long ticketId) {
        deleteFilesAfterCommit(attachments.findStorageKeysByTicketId(ticketId));
    }

    private void deleteFilesAfterCommit(List<String> keys) {
        if (keys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(keys);
            }
        });
    }

    private void deleteFilesOnRollback(List<String> keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(keys);
                }
            }
        });
    }

    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (IOException | RuntimeException e) {
                log.warn("Could not delete stored attachment file {}", key);
            }
        }
    }

    private TicketAttachment find(Long ticketId, Long attachmentId) {
        return attachments.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new NotFoundException("Attachment " + attachmentId + " not found"));
    }

    private static boolean canDelete(Viewer viewer, Ticket ticket, TicketAttachment attachment) {
        return viewer.isAdmin() || (!ticket.getStatus().isFinal() && viewer.is(attachment.getUploader().getId()));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
