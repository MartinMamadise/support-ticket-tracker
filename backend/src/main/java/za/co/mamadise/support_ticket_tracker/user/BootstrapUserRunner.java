package za.co.mamadise.support_ticket_tracker.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Optional, opt-in creation of the very first account so a fresh installation can be signed in to before user
 * administration exists. Off unless {@code app.bootstrap-user.enabled=true}. The credentials come only from
 * environment variables; nothing is built in. It does nothing once any account exists, so it can never reset or add
 * accounts later. Its role comes from {@code app.bootstrap-user.role} (default ADMIN, so a fresh installation has someone who can manage
 * roles; set USER for an unprivileged first account). Remove the variables after first start.
 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap-user", name = "enabled", havingValue = "true")
public class BootstrapUserRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUserRunner.class);

    private final UserService userService;
    private final AppUserRepository users;
    private final String username;
    private final String email;
    private final String password;
    private final Role role;

    public BootstrapUserRunner(UserService userService, AppUserRepository users,
            @Value("${app.bootstrap-user.username:}") String username,
            @Value("${app.bootstrap-user.email:}") String email,
            @Value("${app.bootstrap-user.password:}") String password,
            @Value("${app.bootstrap-user.role:ADMIN}") String role) {
        this.userService = userService;
        this.users = users;
        this.username = username;
        this.email = email;
        this.password = password;
        try {
            this.role = Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.bootstrap-user.role must be one of "
                    + java.util.Arrays.toString(Role.values()), ex);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Bootstrap user skipped: accounts already exist");
            return;
        }
        try {
            UserResponse created = userService.createUser(username, email, password, role);
            log.info("Bootstrap user '{}' created with role {}. Remove the BOOTSTRAP_USER_* variables now.", created.username(), created.role());
        } catch (InvalidUserException ex) {
            // Field names and rule messages only; the password itself is never logged.
            throw new IllegalStateException("app.bootstrap-user is enabled but invalid: " + ex.getFieldErrors(), ex);
        }
    }
}
