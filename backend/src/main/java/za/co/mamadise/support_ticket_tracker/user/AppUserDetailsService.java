package za.co.mamadise.support_ticket_tracker.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads an account for Spring Security by username or, when the text contains '@', by email. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalised = UserService.normalise(identifier);
        // Usernames cannot contain '@' (see UserService), so the two lookups can never be ambiguous.
        return (normalised.contains("@") ? users.findByEmail(normalised) : users.findByUsername(normalised))
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
