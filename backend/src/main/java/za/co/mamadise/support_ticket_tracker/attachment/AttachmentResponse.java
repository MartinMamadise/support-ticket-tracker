package za.co.mamadise.support_ticket_tracker.attachment;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.user.UserSummary;

@Schema(description = "A file attached to a ticket (metadata only; download it from downloadUrl)")
public record AttachmentResponse(
        Long id,
        Long ticketId,
        @Schema(description = "Sanitised original file name", example = "error-screenshot.png") String filename,
        @Schema(description = "Detected by the server from the content", example = "image/png") String contentType,
        long sizeBytes,
        UserSummary uploadedBy,
        Instant createdAt,
        @Schema(example = "/api/tickets/42/attachments/7/download") String downloadUrl,
        @Schema(description = "Whether the caller may delete it") boolean canDelete) {

    static AttachmentResponse from(TicketAttachment a, boolean canDelete) {
        return new AttachmentResponse(a.getId(), a.getTicketId(), a.getFilename(), a.getContentType(), a.getSizeBytes(),
                UserSummary.of(a.getUploader()), a.getCreatedAt(),
                "/api/tickets/" + a.getTicketId() + "/attachments/" + a.getId() + "/download", canDelete);
    }
}
