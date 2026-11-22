package za.co.mamadise.support_ticket_tracker.team;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Ids of every team the account belongs to (one indexed query; used for ticket visibility). */
    @Query("select t.id from Team t join t.members m where m.id = :userId")
    List<Long> findTeamIdsByMemberId(@Param("userId") Long userId);

    /** All teams with their members loaded in the same query (there are few teams). */
    @Query("select distinct t from Team t left join fetch t.members order by t.name")
    List<Team> findAllWithMembers();

    @Query("select t from Team t left join fetch t.members where t.id = :id")
    Optional<Team> findWithMembersById(@Param("id") Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
