package za.co.mamadise.support_ticket_tracker.auth;

import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import za.co.mamadise.support_ticket_tracker.team.TeamRepository;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserPrincipal;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.Role;
import za.co.mamadise.support_ticket_tracker.user.UserService;

/**
 * Resolves the {@link Viewer} of the current request. Services call {@link #require()} at the start of every operation
 * that depends on who is asking, so ownership and team checks never rely on anything the client sent.
 * Fails closed: without a signed-in, enabled account it throws (HTTP 401).
 */
@Component
public class CurrentUser {

    private final AppUserRepository users;
    private final TeamRepository teams;

    public CurrentUser(AppUserRepository users, TeamRepository teams) {
        this.users = users;
        this.teams = teams;
    }

    public Viewer require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationFailedException("Authentication required");
        }
        Long id;
        Role role;
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            // SessionRoleRefreshFilter has already re-read this account for the current request.
            id = principal.getId();
            role = principal.getRole();
        } else {
            // Any other authentication type (not used by the application itself): trust only the database.
            AppUser account = users.findByUsername(UserService.normalise(authentication.getName()))
                    .filter(AppUser::isEnabled)
                    .orElseThrow(() -> new AuthenticationFailedException("Authentication required"));
            id = account.getId();
            role = account.getRole();
        }
        Set<Long> teamIds = role == Role.USER ? Set.of() : Set.copyOf(teams.findTeamIdsByMemberId(id));
        return new Viewer(id, role, teamIds);
    }
}
