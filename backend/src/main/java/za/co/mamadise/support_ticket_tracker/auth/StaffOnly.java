package za.co.mamadise.support_ticket_tracker.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/** Administrators and support agents. Put on a controller or service method (or class); needs method security, enabled in SecurityConfig. */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize(Roles.STAFF)
public @interface StaffOnly {
}
