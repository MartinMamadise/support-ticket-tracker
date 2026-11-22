package za.co.mamadise.support_ticket_tracker.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Repository slice test on an embedded H2 database. The slice builds its own schema from the entity mapping
 * (Flyway is switched off here) so it stays fast and independent of the migration setup; that the migrations
 * produce a schema matching the entities is verified by {@link DatabaseMigrationTest}.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TicketRepositoryTest {

    @Autowired
    private TicketRepository ticketRepository;

    private Ticket newTicket(String title, TicketStatus status, TicketPriority priority) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription("Description for " + title);
        // Set directly: this test exercises persistence, not the workflow (covered by TicketTest).
        ReflectionTestUtils.setField(ticket, "status", status);
        if (status.isResolution()) {
            ReflectionTestUtils.setField(ticket, "resolvedAt", Instant.parse("2026-11-21T12:00:00Z"));
        }
        ticket.setPriority(priority);
        return ticket;
    }

    @Test
    void shouldSaveAndFindTicket() {
        Ticket saved = ticketRepository.save(newTicket("Laptop won't start", TicketStatus.OPEN, TicketPriority.HIGH));

        assertThat(saved.getId()).isNotNull();
        assertThat(ticketRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void shouldSetTimestampsOnSave() {
        Ticket saved = ticketRepository.saveAndFlush(newTicket("Printer jam", TicketStatus.OPEN, TicketPriority.LOW));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindOnlyTicketsWithMatchingStatus() {
        ticketRepository.save(newTicket("Internet connection issue", TicketStatus.OPEN, TicketPriority.HIGH));
        ticketRepository.save(newTicket("Old monitor request", TicketStatus.CLOSED, TicketPriority.LOW));

        List<Ticket> openTickets = ticketRepository.findByStatus(TicketStatus.OPEN);

        assertThat(openTickets).hasSize(1);
        assertThat(openTickets.get(0).getTitle()).isEqualTo("Internet connection issue");
    }

    @Test
    void shouldPersistCancelledStatus() {
        ticketRepository.save(newTicket("Withdrawn", TicketStatus.CANCELLED, TicketPriority.LOW));

        assertThat(ticketRepository.findByStatus(TicketStatus.CANCELLED)).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenNoTicketHasStatus() {
        ticketRepository.save(newTicket("Some ticket", TicketStatus.OPEN, TicketPriority.LOW));

        assertThat(ticketRepository.findByStatus(TicketStatus.RESOLVED)).isEmpty();
    }

    @Test
    void shouldDeleteTicket() {
        Ticket saved = ticketRepository.save(newTicket("To be deleted", TicketStatus.OPEN, TicketPriority.LOW));

        ticketRepository.deleteById(saved.getId());

        assertThat(ticketRepository.existsById(saved.getId())).isFalse();
    }

    @Test
    void shouldStartAtVersionZeroAndIncrementOnUpdate() {
        Ticket saved = ticketRepository.saveAndFlush(newTicket("Versioned", TicketStatus.OPEN, TicketPriority.LOW));
        assertThat(saved.getVersion()).isZero();

        saved.setTitle("Versioned again");
        Ticket updated = ticketRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldPersistResolutionTimeAndStatusAsEnum() {
        Ticket saved = ticketRepository.saveAndFlush(
                newTicket("Done", TicketStatus.RESOLVED, TicketPriority.MEDIUM));

        Ticket found = ticketRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(found.getResolvedAt()).isEqualTo(Instant.parse("2026-11-21T12:00:00Z"));
    }
}
