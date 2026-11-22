package za.co.mamadise.support_ticket_tracker.activity;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.user.UserSummary;

@Schema(description = "One entry of a ticket's activity timeline")
public record ActivityResponse(
        Long id,
        ActivityType type,
        @Schema(description = "Who did it; null for automatic changes", nullable = true) UserSummary actor,
        TicketActivity.EntityType entityType,
        @Schema(nullable = true) Long entityId,
        @Schema(nullable = true, example = "status") String field,
        @Schema(nullable = true, example = "OPEN") String oldValue,
        @Schema(nullable = true, example = "IN_PROGRESS") String newValue,
        @Schema(description = "True for staff-only entries (never returned to requesters)") boolean internal,
        Instant createdAt) {

    static ActivityResponse from(TicketActivity a) {
        return new ActivityResponse(a.getId(), a.getType(), UserSummary.of(a.getActor()), a.getEntityType(),
                a.getEntityId(), a.getFieldName(), a.getOldValue(), a.getNewValue(), a.isInternal(), a.getCreatedAt());
    }
}
