package za.co.mamadise.support_ticket_tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Runs against the in-memory H2 database configured in src/test/resources. */
@DataJpaTest
class TicketRepositoryTest {

    @Autowired
    private TicketRepository ticketRepository;

    private Ticket newTicket(String title, String status, String priority) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription("Description for " + title);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        return ticket;
    }

    @Test
    void shouldSaveAndFindTicket() {
        Ticket saved = ticketRepository.save(newTicket("Laptop won't start", "OPEN", "HIGH"));

        assertThat(saved.getId()).isNotNull();
        assertThat(ticketRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void shouldSetTimestampsOnSave() {
        Ticket saved = ticketRepository.saveAndFlush(newTicket("Printer jam", "OPEN", "LOW"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindOnlyTicketsWithMatchingStatus() {
        ticketRepository.save(newTicket("Internet connection issue", "OPEN", "HIGH"));
        ticketRepository.save(newTicket("Old monitor request", "CLOSED", "LOW"));

        List<Ticket> openTickets = ticketRepository.findByStatus("OPEN");

        assertThat(openTickets).hasSize(1);
        assertThat(openTickets.get(0).getTitle()).isEqualTo("Internet connection issue");
    }

    @Test
    void shouldReturnEmptyListWhenNoTicketHasStatus() {
        ticketRepository.save(newTicket("Some ticket", "OPEN", "LOW"));

        assertThat(ticketRepository.findByStatus("RESOLVED")).isEmpty();
    }

    @Test
    void shouldDeleteTicket() {
        Ticket saved = ticketRepository.save(newTicket("To be deleted", "OPEN", "LOW"));

        ticketRepository.deleteById(saved.getId());

        assertThat(ticketRepository.existsById(saved.getId())).isFalse();
    }
}
