package za.co.mamadise.support_ticket_tracker.sla;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.error.BadRequestException;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;

/**
 * Applies the SLA rules (see {@link SlaMath}) to tickets as they change, and lets administrators edit the targets.
 * Changing a target affects tickets whose deadlines are (re)computed afterwards (new tickets, priority changes,
 * reopen, resume after WAITING); existing deadlines are not rewritten retroactively.
 */
@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final SlaPolicyRepository policies;

    public SlaService(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    /** The targets for a priority. Falls back to the V10 defaults if a row is missing (never blocks ticket work). */
    @Transactional(readOnly = true)
    public SlaTarget targetFor(TicketPriority priority) {
        return policies.findById(priority).map(SlaPolicy::target).orElseGet(() -> defaultTarget(priority));
    }

    static SlaTarget defaultTarget(TicketPriority priority) {
        return switch (priority) {
            case CRITICAL -> new SlaTarget(15, 240);
            case HIGH -> new SlaTarget(60, 480);
            case MEDIUM -> new SlaTarget(240, 1440);
            case LOW -> new SlaTarget(480, 4320);
        };
    }

    public void onCreated(Ticket ticket, Instant now) {
        ticket.startSla(targetFor(ticket.getPriority()), now);
    }

    public void onPriorityChanged(Ticket ticket) {
        ticket.recalculateSla(targetFor(ticket.getPriority()));
    }

    /** Call after {@link Ticket#changeStatus} succeeded. */
    public void onStatusChanged(Ticket ticket, TicketStatus from, TicketStatus to, Instant now) {
        if (to == TicketStatus.WAITING) {
            ticket.pauseSla(now);
        } else if (from == TicketStatus.WAITING) {
            ticket.resumeSla(targetFor(ticket.getPriority()), now);
        }
        if (from.isResolution() && to == TicketStatus.OPEN) {
            ticket.restartResolutionSla(targetFor(ticket.getPriority()), now);
        }
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> listPolicies() {
        return Arrays.stream(TicketPriority.values())
                .map(p -> policies.findById(p).orElseGet(() -> {
                    SlaTarget d = defaultTarget(p);
                    return new SlaPolicy(p, d.responseMinutes(), d.resolutionMinutes());
                }))
                .map(SlaPolicyResponse::from)
                .toList();
    }

    @Transactional
    public SlaPolicyResponse updatePolicy(TicketPriority priority, SlaPolicyRequest request) {
        if (request.resolutionMinutes() < request.responseMinutes()) {
            throw BadRequestException.field("resolutionMinutes",
                    "resolutionMinutes must not be smaller than responseMinutes");
        }
        SlaTarget target = new SlaTarget(request.responseMinutes(), request.resolutionMinutes());
        SlaPolicy policy = policies.findById(priority)
                .orElseGet(() -> new SlaPolicy(priority, target.responseMinutes(), target.resolutionMinutes()));
        policy.change(target);
        SlaPolicy saved = policies.saveAndFlush(policy);
        log.info("SLA policy {} changed to response {} min, resolution {} min", priority, target.responseMinutes(),
                target.resolutionMinutes());
        return SlaPolicyResponse.from(saved);
    }
}
