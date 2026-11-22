package za.co.mamadise.support_ticket_tracker.assignment;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, Long> {

    @EntityGraph(attributePaths = {"previousAssignee", "newAssignee", "previousTeam", "newTeam", "actor"})
    List<TicketAssignment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
