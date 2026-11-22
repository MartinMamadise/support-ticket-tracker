package za.co.mamadise.support_ticket_tracker.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.auth.Viewer;
import za.co.mamadise.support_ticket_tracker.ticket.Ticket;
import za.co.mamadise.support_ticket_tracker.ticket.TicketAccessPolicy;
import za.co.mamadise.support_ticket_tracker.ticket.TicketCategory;
import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;

/**
 * Builds the dashboard with aggregate SQL (COUNT / AVG with GROUP BY), always restricted by
 * {@link TicketAccessPolicy#visibility}. The only per-row data loaded is the creation/resolution timestamps of the
 * trend window (at most {@value #MAX_TREND_DAYS} days), bucketed per UTC day in Java to stay portable between MySQL
 * and H2.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    public static final int DEFAULT_TREND_DAYS = 14;
    public static final int MAX_TREND_DAYS = 90;

    private final EntityManager em;
    private final CurrentUser currentUser;
    private final WorkloadService workloadService;
    private final Clock clock;

    public DashboardService(EntityManager em, CurrentUser currentUser, WorkloadService workloadService, Clock clock) {
        this.em = em;
        this.currentUser = currentUser;
        this.workloadService = workloadService;
        this.clock = clock;
    }

    public DashboardResponse dashboard(int trendDays) {
        Viewer viewer = currentUser.require();
        Instant now = clock.instant();
        int days = Math.max(1, Math.min(trendDays, MAX_TREND_DAYS));

        Map<TicketStatus, Long> byStatus = groupCount(viewer, "status", TicketStatus.class);
        Map<TicketPriority, Long> byPriority = groupCount(viewer, "priority", TicketPriority.class);
        Map<TicketCategory, Long> byCategory = groupCount(viewer, "category", TicketCategory.class);

        var active = TicketStatus.activeStatuses();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long urgent = count(viewer, (r, cb) -> cb.and(r.get("status").in(active),
                r.get("priority").in(TicketPriority.HIGH, TicketPriority.CRITICAL)));
        long critical = count(viewer, (r, cb) -> cb.and(r.get("status").in(active),
                cb.equal(r.get("priority"), TicketPriority.CRITICAL)));
        long unassigned = count(viewer, (r, cb) -> cb.and(r.get("status").in(active), cb.isNull(r.get("assigneeId"))));
        long overdue = count(viewer, (r, cb) -> cb.and(r.get("status").in(active), cb.isNull(r.get("slaPausedAt")),
                cb.lessThan(r.<Instant>get("resolutionDueAt"), now)));

        DashboardResponse.Totals totals = new DashboardResponse.Totals(total,
                byStatus.get(TicketStatus.OPEN), byStatus.get(TicketStatus.IN_PROGRESS), byStatus.get(TicketStatus.WAITING),
                byStatus.get(TicketStatus.RESOLVED), byStatus.get(TicketStatus.CLOSED), byStatus.get(TicketStatus.CANCELLED),
                urgent, critical, unassigned, overdue);

        return new DashboardResponse(
                viewer.isAdmin() ? "all" : viewer.isAgent() ? "work-queue" : "own",
                totals, byStatus, byPriority, byCategory,
                sla(viewer, now),
                averageResolutionHours(viewer),
                trend(viewer, now, days),
                viewer.isStaff() ? workloadService.workload() : null);
    }

    private DashboardResponse.Sla sla(Viewer viewer, Instant now) {
        // Response: decided = responded (on time or late) or overdue without a response.
        long responseMet = count(viewer, (r, cb) -> cb.and(cb.isNotNull(r.get("responseDueAt")),
                cb.isNotNull(r.get("firstResponseAt")),
                cb.lessThanOrEqualTo(r.<Instant>get("firstResponseAt"), r.<Instant>get("responseDueAt"))));
        long responseLate = count(viewer, (r, cb) -> cb.and(cb.isNotNull(r.get("responseDueAt")),
                cb.isNotNull(r.get("firstResponseAt")),
                cb.greaterThan(r.<Instant>get("firstResponseAt"), r.<Instant>get("responseDueAt"))));
        long responseOverdue = count(viewer, (r, cb) -> cb.and(r.get("status").in(TicketStatus.activeStatuses()),
                cb.isNull(r.get("firstResponseAt")), cb.lessThan(r.<Instant>get("responseDueAt"), now)));
        long responseMeasured = responseMet + responseLate + responseOverdue;

        // Resolution: decided = resolved/closed with a deadline, or active and overdue (not paused).
        var resolution = java.util.EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);
        long resolutionMet = count(viewer, (r, cb) -> cb.and(r.get("status").in(resolution),
                cb.isNotNull(r.get("resolutionDueAt")),
                cb.lessThanOrEqualTo(r.<Instant>get("resolvedAt"), r.<Instant>get("resolutionDueAt"))));
        long resolutionLate = count(viewer, (r, cb) -> cb.and(r.get("status").in(resolution),
                cb.isNotNull(r.get("resolutionDueAt")),
                cb.greaterThan(r.<Instant>get("resolvedAt"), r.<Instant>get("resolutionDueAt"))));
        long resolutionOverdue = count(viewer, (r, cb) -> cb.and(r.get("status").in(TicketStatus.activeStatuses()),
                cb.isNull(r.get("slaPausedAt")), cb.lessThan(r.<Instant>get("resolutionDueAt"), now)));
        long resolutionMeasured = resolutionMet + resolutionLate + resolutionOverdue;

        long atRisk = count(viewer, (r, cb) -> cb.and(r.get("status").in(TicketStatus.activeStatuses()),
                cb.isNull(r.get("slaPausedAt")),
                cb.lessThanOrEqualTo(r.<Instant>get("resolutionWarningAt"), now),
                cb.greaterThanOrEqualTo(r.<Instant>get("resolutionDueAt"), now)));

        return new DashboardResponse.Sla(percent(responseMet, responseMeasured), responseMeasured,
                percent(resolutionMet, resolutionMeasured), resolutionMeasured, responseOverdue, atRisk);
    }

    private Double averageResolutionHours(Viewer viewer) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Double> q = cb.createQuery(Double.class);
        Root<Ticket> r = q.from(Ticket.class);
        q.select(cb.avg(r.<Long>get("resolutionSeconds")))
                .where(TicketAccessPolicy.visibility(viewer, r, cb), cb.isNotNull(r.get("resolutionSeconds")));
        Double seconds = em.createQuery(q).getSingleResult();
        return seconds == null ? null : Math.round(seconds / 36.0) / 100.0; // hours, 2 decimals
    }

    private List<DashboardResponse.TrendPoint> trend(Viewer viewer, Instant now, int days) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate first = today.minusDays(days - 1L);
        Instant start = first.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            buckets.put(d, new long[2]);
        }
        for (Instant created : timestamps(viewer, "createdAt", start)) {
            long[] bucket = buckets.get(LocalDate.ofInstant(created, ZoneOffset.UTC));
            if (bucket != null) {
                bucket[0]++;
            }
        }
        for (Instant resolved : timestamps(viewer, "resolvedAt", start)) {
            long[] bucket = buckets.get(LocalDate.ofInstant(resolved, ZoneOffset.UTC));
            if (bucket != null) {
                bucket[1]++;
            }
        }
        List<DashboardResponse.TrendPoint> points = new ArrayList<>();
        buckets.forEach((date, v) -> points.add(new DashboardResponse.TrendPoint(date, v[0], v[1])));
        return points;
    }

    /** One timestamp column of the visible tickets since {@code start}: never whole entities. */
    private List<Instant> timestamps(Viewer viewer, String attribute, Instant start) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Instant> q = cb.createQuery(Instant.class);
        Root<Ticket> r = q.from(Ticket.class);
        Expression<Instant> column = r.get(attribute);
        q.select(column).where(TicketAccessPolicy.visibility(viewer, r, cb), cb.greaterThanOrEqualTo(column, start));
        return em.createQuery(q).getResultList();
    }

    private <E extends Enum<E>> Map<E, Long> groupCount(Viewer viewer, String attribute, Class<E> type) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Ticket> r = q.from(Ticket.class);
        Expression<E> key = r.get(attribute);
        q.multiselect(key, cb.count(r)).where(TicketAccessPolicy.visibility(viewer, r, cb)).groupBy(key);
        Map<E, Long> result = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            result.put(constant, 0L);
        }
        for (Tuple row : em.createQuery(q).getResultList()) {
            result.put(row.get(0, type), row.get(1, Long.class));
        }
        return result;
    }

    private long count(Viewer viewer, BiFunction<Root<Ticket>, CriteriaBuilder, Predicate> condition) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Ticket> r = q.from(Ticket.class);
        q.select(cb.count(r)).where(TicketAccessPolicy.visibility(viewer, r, cb), condition.apply(r, cb));
        return em.createQuery(q).getSingleResult();
    }

    private static Double percent(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 10.0;
    }
}
