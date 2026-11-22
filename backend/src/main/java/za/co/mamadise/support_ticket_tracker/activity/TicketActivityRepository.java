package za.co.mamadise.support_ticket_tracker.activity;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {

    @EntityGraph(attributePaths = "actor")
    List<TicketActivity> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    /** Public entries only: what a requester may see. Filtered in SQL, not after loading. */
    @EntityGraph(attributePaths = "actor")
    List<TicketActivity> findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(Long ticketId);
}
