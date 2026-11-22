package za.co.mamadise.support_ticket_tracker.user;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * What Spring Security knows about a signed-in account. It is stored in the HTTP session, so the password hash is
 * erased as soon as authentication succeeds ({@link CredentialsContainer}). The single authority comes from the persisted role (ROLE_ADMIN, ROLE_SUPPORT_AGENT or ROLE_USER).
 */
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String email;
    private final boolean enabled;
    private final Role role;
    private String passwordHash;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(role.toGrantedAuthority());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", username=" + username + ", role=" + role + "]";
    }
}
