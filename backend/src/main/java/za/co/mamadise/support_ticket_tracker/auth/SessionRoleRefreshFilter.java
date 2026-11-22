package za.co.mamadise.support_ticket_tracker.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserPrincipal;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * Keeps a long-lived session honest. The principal in the session is a snapshot taken at login, so on every request
 * from a signed-in account this filter re-reads the account (one primary-key lookup):
 * <ul>
 * <li>account deleted or disabled: the session is ended and the request continues as anonymous (401);</li>
 * <li>role changed: the authentication is replaced by one carrying the current role, and saved to the session,
 * so a promotion or demotion applies to the very next request without signing in again.</li>
 * </ul>
 * Only sessions holding an {@link AppUserPrincipal} are touched; it never creates a session or an authentication.
 */
final class SessionRoleRefreshFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    SessionRoleRefreshFilter(AppUserRepository users, SecurityContextRepository securityContextRepository) {
        this.users = users;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = contextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            AppUser current = users.findById(principal.getId()).filter(AppUser::isEnabled).orElse(null);
            if (current == null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                contextHolder.clearContext();
            } else if (current.getRole() != principal.getRole()) {
                AppUserPrincipal refreshed = new AppUserPrincipal(current);
                refreshed.eraseCredentials();
                SecurityContext updated = contextHolder.createEmptyContext();
                updated.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(refreshed, null,
                        refreshed.getAuthorities()));
                contextHolder.setContext(updated);
                securityContextRepository.saveContext(updated, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
