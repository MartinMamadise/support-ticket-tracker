package za.co.mamadise.support_ticket_tracker.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The only role definition in the application. Every account has exactly one role, stored by name in
 * {@code app_users.role} (a CHECK constraint in migration V5 lists the same names; adding a value needs a migration).
 * The Spring Security authority is always {@code ROLE_<NAME>}, so {@code hasRole('ADMIN')} matches {@link #ADMIN}.
 * There is no implicit hierarchy: rules that admit several roles name them (see {@code Roles}).
 */
public enum Role {

    /** Administration: user and role management and other privileged operations. */
    ADMIN,
    /** Support staff. */
    SUPPORT_AGENT,
    /** Ordinary account and the default for everyone. */
    USER;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    /** The authority string, e.g. {@code ROLE_SUPPORT_AGENT}. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public GrantedAuthority toGrantedAuthority() {
        return new SimpleGrantedAuthority(authority());
    }
}
