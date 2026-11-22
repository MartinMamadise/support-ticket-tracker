package za.co.mamadise.support_ticket_tracker.ticket;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link JpaSpecificationExecutor} provides the filtered, sorted, paged query used by the ticket list. Requester,
 * assignee and team are fetched in the same query (entity graph) so mapping a page to DTOs causes no extra queries.
 * Aggregates are grouped in SQL and return {@code [key, count]} rows.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByStatus(TicketStatus status);

    @Override
    @EntityGraph(attributePaths = {"requester", "assignee", "team"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "assignee", "team"})
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findDetailedById(@Param("id") Long id);

    List<Ticket> findByAssigneeIdAndStatusIn(Long assigneeId, Collection<TicketStatus> statuses);

    long countByAssigneeIdIsNullAndStatusIn(Collection<TicketStatus> statuses);

    @Query("select t.teamId, count(t) from Ticket t where t.teamId is not null and t.status in :statuses "
            + "group by t.teamId")
    List<Object[]> countByTeamForStatuses(@Param("statuses") Collection<TicketStatus> statuses);

    @Query("select t.teamId, count(t) from Ticket t where t.teamId is not null and t.assigneeId is null "
            + "and t.status in :statuses group by t.teamId")
    List<Object[]> countUnassignedByTeamForStatuses(@Param("statuses") Collection<TicketStatus> statuses);

    @Query("select t.assigneeId, count(t) from Ticket t where t.assigneeId is not null and t.status in :statuses "
            + "group by t.assigneeId")
    List<Object[]> countByAssigneeForStatuses(@Param("statuses") Collection<TicketStatus> statuses);

    @Query("select t.assigneeId, count(t) from Ticket t where t.assigneeId is not null and t.status in :statuses "
            + "and t.priority in :priorities group by t.assigneeId")
    List<Object[]> countByAssigneeForStatusesAndPriorities(@Param("statuses") Collection<TicketStatus> statuses,
            @Param("priorities") Collection<TicketPriority> priorities);

    @Query("select t.assigneeId, count(t) from Ticket t where t.assigneeId is not null and t.status in :statuses "
            + "and t.slaPausedAt is null and t.resolutionDueAt < :now group by t.assigneeId")
    List<Object[]> countOverdueByAssignee(@Param("statuses") Collection<TicketStatus> statuses,
            @Param("now") Instant now);

    @Query("select t.teamId, count(t) from Ticket t where t.teamId is not null and t.status in :statuses "
            + "and t.slaPausedAt is null and t.resolutionDueAt < :now group by t.teamId")
    List<Object[]> countOverdueByTeam(@Param("statuses") Collection<TicketStatus> statuses, @Param("now") Instant now);

    // --- SLA monitor: one bounded batch per run ---

    @Query("select t from Ticket t where t.status in :statuses and t.firstResponseAt is null "
            + "and t.responseBreachNotified = false and t.responseDueAt < :now order by t.responseDueAt")
    List<Ticket> findUnnotifiedResponseBreaches(@Param("statuses") Collection<TicketStatus> statuses,
            @Param("now") Instant now, Pageable limit);

    @Query("select t from Ticket t where t.status in :statuses and t.slaPausedAt is null "
            + "and t.resolutionWarningNotified = false and t.resolutionWarningAt <= :now and t.resolutionDueAt >= :now "
            + "order by t.resolutionDueAt")
    List<Ticket> findUnnotifiedResolutionWarnings(@Param("statuses") Collection<TicketStatus> statuses,
            @Param("now") Instant now, Pageable limit);

    @Query("select t from Ticket t where t.status in :statuses and t.slaPausedAt is null "
            + "and t.resolutionBreachNotified = false and t.resolutionDueAt < :now order by t.resolutionDueAt")
    List<Ticket> findUnnotifiedResolutionBreaches(@Param("statuses") Collection<TicketStatus> statuses,
            @Param("now") Instant now, Pageable limit);
}
