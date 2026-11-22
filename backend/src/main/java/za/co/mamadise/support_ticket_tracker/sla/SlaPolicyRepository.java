package za.co.mamadise.support_ticket_tracker.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, TicketPriority> {
}
