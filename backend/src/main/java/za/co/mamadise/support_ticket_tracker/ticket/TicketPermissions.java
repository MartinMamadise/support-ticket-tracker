package za.co.mamadise.support_ticket_tracker.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import za.co.mamadise.support_ticket_tracker.auth.Viewer;

/**
 * What the current caller may do with this ticket, so the UI can hide actions that would be refused.
 * Informational only: every operation is checked again on the server.
 */
@Schema(description = "Actions the current caller may perform on this ticket (the server enforces them anyway)")
public record TicketPermissions(
        boolean canEdit,
        boolean canChangeStatus,
        boolean canAssign,
        boolean canComment,
        boolean canAddInternalNote,
        boolean canViewInternal,
        boolean canUpload,
        boolean canDelete) {

    public static TicketPermissions of(Viewer viewer, Ticket ticket) {
        return new TicketPermissions(
                TicketAccessPolicy.canEditDetails(viewer, ticket),
                !TicketAccessPolicy.allowedTransitions(viewer, ticket).isEmpty(),
                TicketAccessPolicy.canAssign(viewer, ticket),
                TicketAccessPolicy.canComment(viewer, ticket),
                TicketAccessPolicy.canAddInternalNote(viewer, ticket),
                TicketAccessPolicy.canSeeInternal(viewer, ticket),
                TicketAccessPolicy.canUpload(viewer, ticket),
                TicketAccessPolicy.canDelete(viewer));
    }
}
