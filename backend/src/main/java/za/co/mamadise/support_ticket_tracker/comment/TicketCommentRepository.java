package za.co.mamadise.support_ticket_tracker.comment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    /** Public comments only: what a requester may see. Internal notes are excluded in SQL. */
    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(Long ticketId);

    @EntityGraph(attributePaths = "author")
    Optional<TicketComment> findByIdAndTicketId(Long id, Long ticketId);
}
