package za.co.mamadise.support_ticket_tracker.comment;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.user.UserSummary;

@Schema(description = "A public comment, or an internal note (only ever returned to staff)")
public record CommentResponse(
        Long id,
        Long ticketId,
        UserSummary author,
        @Schema(description = "Plain text; clients must display it as text, not HTML") String body,
        boolean internal,
        boolean edited,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Whether the caller may edit this comment") boolean canEdit) {

    static CommentResponse from(TicketComment c, boolean canEdit) {
        return new CommentResponse(c.getId(), c.getTicketId(), UserSummary.of(c.getAuthor()), c.getBody(),
                c.isInternal(), c.isEdited(), c.getCreatedAt(), c.getUpdatedAt(), canEdit);
    }
}
