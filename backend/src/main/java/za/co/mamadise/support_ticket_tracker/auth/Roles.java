package za.co.mamadise.support_ticket_tracker.auth;

/**
 * SpEL expressions for method security, in one place so rules are spelled the same everywhere. Prefer the
 * meta-annotations {@link AdminOnly}, {@link StaffOnly} and {@link AnyRole} over writing {@code @PreAuthorize}
 * by hand. The names must match {@code user.Role} (a test checks this).
 */
public final class Roles {

    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String STAFF = "hasAnyRole('ADMIN','SUPPORT_AGENT')";
    public static final String ANY = "hasAnyRole('ADMIN','SUPPORT_AGENT','USER')";

    private Roles() {
    }
}
