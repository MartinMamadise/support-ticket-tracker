package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.activity.ActivityService;
import za.co.mamadise.support_ticket_tracker.activity.ActivityType;
import za.co.mamadise.support_ticket_tracker.attachment.AttachmentService;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.error.ForbiddenException;
import za.co.mamadise.support_ticket_tracker.sla.SlaService;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * Business logic for tickets: creation defaults, visibility, update rules and the workflow. Every operation first
 * resolves who is asking ({@link CurrentUser}) and applies {@link TicketAccessPolicy}; status changes always go through
 * {@link TicketStatusChanger} (and so through {@link Ticket#changeStatus}), so neither the workflow nor the
 * permissions can be bypassed. Entities never leave this class: callers get {@link TicketResponse}s.
 */
@Service
@Transactional
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketLookup lookup;
    private final TicketStatusChanger statusChanger;
    private final CurrentUser currentUser;
    private final AppUserRepository users;
    private final SlaService sla;
    private final ActivityService activity;
    private final AttachmentService attachments;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository, TicketLookup lookup, TicketStatusChanger statusChanger,
            CurrentUser currentUser, AppUserRepository users, SlaService sla, ActivityService activity,
            AttachmentService attachments, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.lookup = lookup;
        this.statusChanger = statusChanger;
        this.currentUser = currentUser;
        this.users = users;
        this.sla = sla;
        this.activity = activity;
        this.attachments = attachments;
        this.clock = clock;
    }

    /** One page of the tickets the caller may see; filtering, search, sorting and paging all happen in SQL. */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> listTickets(TicketListQuery query) {
        Viewer viewer = currentUser.require();
        Instant now = clock.instant();
        return PageResponse.from(
                ticketRepository.findAll(TicketSpecifications.from(query, viewer), query.toPageable()),
                t -> TicketResponse.from(t, viewer, now));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Viewer viewer = currentUser.require();
        return TicketResponse.from(lookup.requireVisible(id, viewer), viewer, clock.instant());
    }

    /** Creates a ticket raised by the caller. It always starts OPEN, unassigned, with SLA clocks running. */
    public TicketResponse createTicket(TicketRequest request) {
        Viewer viewer = currentUser.require();
        TicketStatus status = request.statusOrNull();
        if (status != null && status != TicketStatus.OPEN) {
            throw TicketWorkflowException.invalidInitialStatus(status);
        }
        Instant now = clock.instant();
        Ticket ticket = new Ticket();
        applyText(ticket, request);
        ticket.setCategory(request.categoryOrNull());
        ticket.setRequester(users.getReferenceById(viewer.id()));
        ticket.setCreatedAt(now);
        sla.onCreated(ticket, now);
        // Flush so generated values (id, timestamps, version) are present in the response.
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        activity.record(saved.getId(), viewer.id(), ActivityType.CREATED);
        return TicketResponse.from(saved, viewer, now);
    }

    /**
     * Replaces title, description, priority and (when sent) category, and optionally changes the status.
     * Requesters without work access may only change the details while the ticket is OPEN (403 otherwise).
     */
    public TicketResponse updateTicket(Long id, TicketRequest request) {
        Viewer viewer = currentUser.require();
        Ticket existing = lookup.requireVisible(id, viewer);
        checkVersion(existing, request.version());
        if (existing.getStatus().isFinal()) {
            throw TicketWorkflowException.finalTicketNotEditable(existing.getStatus());
        }
        Instant now = clock.instant();

        String title = request.title().trim();
        String description = request.description().trim();
        TicketPriority priority = request.priorityValue();
        TicketCategory category = request.categoryOrNull() == null ? existing.getCategory() : request.categoryOrNull();
        boolean detailsChange = !title.equals(existing.getTitle()) || !description.equals(existing.getDescription())
                || priority != existing.getPriority() || category != existing.getCategory();
        if (detailsChange && !TicketAccessPolicy.canEditDetails(viewer, existing)) {
            throw new ForbiddenException("You can only change the details of your ticket while it is open");
        }

        // Status first: a forbidden transition is rejected before anything is modified.
        TicketStatus status = request.statusOrNull();
        if (status != null) {
            statusChanger.change(existing, status, viewer, now);
        }
        if (detailsChange) {
            if (!Objects.equals(title, existing.getTitle()) || !Objects.equals(description, existing.getDescription())) {
                activity.record(id, viewer.id(), ActivityType.UPDATED);
            }
            if (priority != existing.getPriority()) {
                activity.recordChange(id, viewer.id(), ActivityType.PRIORITY_CHANGED, "priority",
                        existing.getPriority().name(), priority.name());
            }
            if (category != existing.getCategory()) {
                activity.recordChange(id, viewer.id(), ActivityType.CATEGORY_CHANGED, "category",
                        existing.getCategory().name(), category.name());
            }
            boolean priorityChanged = priority != existing.getPriority();
            existing.setTitle(title);
            existing.setDescription(description);
            existing.setPriority(priority);
            existing.setCategory(category);
            if (priorityChanged) {
                sla.onPriorityChanged(existing);
            }
        }
        // Flush so the incremented version and new updatedAt are present in the response.
        return TicketResponse.from(ticketRepository.saveAndFlush(existing), viewer, now);
    }

    /** Status-only change (resolve, reopen, close, cancel, ...). */
    public TicketResponse changeStatus(Long id, StatusChangeRequest request) {
        Viewer viewer = currentUser.require();
        Ticket ticket = lookup.requireVisible(id, viewer);
        checkVersion(ticket, request.version());
        Instant now = clock.instant();
        statusChanger.change(ticket, request.statusValue(), viewer, now);
        return TicketResponse.from(ticketRepository.saveAndFlush(ticket), viewer, now);
    }

    /** Administrators only (method security on the controller); stored attachment files are removed after commit. */
    public void deleteTicket(Long id) {
        Viewer viewer = currentUser.require();
        if (!TicketAccessPolicy.canDelete(viewer)) {
            throw new ForbiddenException("Access denied");
        }
        if (!ticketRepository.existsById(id)) {
            throw new TicketNotFoundException(id);
        }
        attachments.deleteFilesAfterCommit(id);
        ticketRepository.deleteById(id);
    }

    private static void checkVersion(Ticket ticket, Long version) {
        if (version != null && !version.equals(ticket.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Ticket.class, ticket.getId());
        }
    }

    private static void applyText(Ticket ticket, TicketRequest request) {
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setPriority(request.priorityValue());
    }
}
