package za.co.mamadise.support_ticket_tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketService ticketService;

    private Ticket existingTicket() {
        Ticket ticket = new Ticket();
        ticket.setTitle("Old title");
        ticket.setDescription("Old description");
        ticket.setStatus("IN_PROGRESS");
        ticket.setPriority("LOW");
        return ticket;
    }

    @Test
    void createTicketDefaultsStatusToOpenAndTrimsText() {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created = ticketService.createTicket(
                new TicketRequest("  Printer broken ", " Paper jam ", null, "HIGH"));

        assertThat(created.getTitle()).isEqualTo("Printer broken");
        assertThat(created.getDescription()).isEqualTo("Paper jam");
        assertThat(created.getStatus()).isEqualTo("OPEN");
        assertThat(created.getPriority()).isEqualTo("HIGH");
    }

    @Test
    void createTicketKeepsSuppliedStatus() {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created = ticketService.createTicket(
                new TicketRequest("Title", "Desc", "RESOLVED", "LOW"));

        assertThat(created.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void getTicketByIdThrowsWhenMissing() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicketById(99L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateTicketChangesFields() {
        Ticket existing = existingTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(existing)).thenReturn(existing);

        Ticket updated = ticketService.updateTicket(1L,
                new TicketRequest("New title", "New description", "RESOLVED", "HIGH"));

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getStatus()).isEqualTo("RESOLVED");
        assertThat(updated.getPriority()).isEqualTo("HIGH");
    }

    @Test
    void updateTicketWithoutStatusKeepsExistingStatus() {
        Ticket existing = existingTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(existing)).thenReturn(existing);

        Ticket updated = ticketService.updateTicket(1L,
                new TicketRequest("Title", "Desc", null, "MEDIUM"));

        assertThat(updated.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateTicketThrowsWhenMissing() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.updateTicket(5L,
                new TicketRequest("Title", "Desc", "OPEN", "LOW")))
                .isInstanceOf(TicketNotFoundException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void deleteTicketRemovesExistingTicket() {
        when(ticketRepository.existsById(3L)).thenReturn(true);

        ticketService.deleteTicket(3L);

        verify(ticketRepository).deleteById(3L);
    }

    @Test
    void deleteTicketThrowsWhenMissing() {
        when(ticketRepository.existsById(3L)).thenReturn(false);

        assertThatThrownBy(() -> ticketService.deleteTicket(3L))
                .isInstanceOf(TicketNotFoundException.class);
        verify(ticketRepository, never()).deleteById(any());
    }
}
