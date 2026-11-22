package za.co.mamadise.support_ticket_tracker.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Active staff (ADMIN, SUPPORT_AGENT): the accounts that can be assigned tickets. */
    @Query("select u from AppUser u where u.enabled = true and u.role in :roles order by u.username")
    List<AppUser> findEnabledByRoleIn(@Param("roles") Collection<Role> roles);

    /** Active staff who are members of the team. */
    @Query("select u from Team t join t.members u where t.id = :teamId and u.enabled = true and u.role in :roles "
            + "order by u.username")
    List<AppUser> findEnabledTeamMembersByRoleIn(@Param("teamId") Long teamId, @Param("roles") Collection<Role> roles);

    @Query("select u from AppUser u where u.enabled = true and u.role = :role")
    List<AppUser> findEnabledByRole(@Param("role") Role role);
}
