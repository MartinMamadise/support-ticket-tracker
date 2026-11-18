package za.co.mamadise.support_ticket_tracker;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TicketService {

    private static final String DEFAULT_STATUS = "OPEN";

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    public Ticket createTicket(TicketRequest request) {
        Ticket ticket = new Ticket();
        apply(ticket, request);
        if (ticket.getStatus() == null) {
            ticket.setStatus(DEFAULT_STATUS);
        }
        return ticketRepository.save(ticket);
    }

    public Ticket updateTicket(Long id, TicketRequest request) {
        Ticket existing = getTicketById(id);
        apply(existing, request);
        return ticketRepository.save(existing);
    }

    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new TicketNotFoundException(id);
        }
        ticketRepository.deleteById(id);
    }

    /** Copies request values onto the ticket; a null status leaves the current status unchanged. */
    private void apply(Ticket ticket, TicketRequest request) {
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setPriority(request.priority());
        if (request.status() != null) {
            ticket.setStatus(request.status());
        }
    }
}
