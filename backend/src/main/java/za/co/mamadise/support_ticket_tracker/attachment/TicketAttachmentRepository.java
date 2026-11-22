package za.co.mamadise.support_ticket_tracker.attachment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    @EntityGraph(attributePaths = "uploader")
    List<TicketAttachment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    @EntityGraph(attributePaths = "uploader")
    Optional<TicketAttachment> findByIdAndTicketId(Long id, Long ticketId);

    long countByTicketId(Long ticketId);

    @Query("select a.storageKey from TicketAttachment a where a.ticketId = :ticketId")
    List<String> findStorageKeysByTicketId(@Param("ticketId") Long ticketId);
}
