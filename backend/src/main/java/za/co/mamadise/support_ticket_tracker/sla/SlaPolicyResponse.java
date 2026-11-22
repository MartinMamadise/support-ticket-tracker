package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Instant;

import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;

public record SlaPolicyResponse(TicketPriority priority, int responseMinutes, int resolutionMinutes, Instant updatedAt) {

    static SlaPolicyResponse from(SlaPolicy p) {
        return new SlaPolicyResponse(p.getPriority(), p.getResponseMinutes(), p.getResolutionMinutes(), p.getUpdatedAt());
    }
}
