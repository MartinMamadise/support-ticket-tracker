package za.co.mamadise.support_ticket_tracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;

/** The reusable method-security annotations, exercised on a probe bean so the rules are proven independently of endpoints. */
@SpringBootTest
@Import(MethodSecurityTest.ProbeConfig.class)
class MethodSecurityTest {

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    static class Probe {
        @AdminOnly
        public String admin() {
            return "admin";
        }

        @StaffOnly
        public String staff() {
            return "staff";
        }

        @AnyRole
        public String any() {
            return "any";
        }
    }

    @Autowired
    private Probe probe;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPassesEveryRule() {
        assertThat(probe.admin()).isEqualTo("admin");
        assertThat(probe.staff()).isEqualTo("staff");
        assertThat(probe.any()).isEqualTo("any");
    }

    @Test
    @WithMockUser(roles = "SUPPORT_AGENT")
    void supportAgentPassesStaffAndAnyButNotAdmin() {
        assertThat(probe.staff()).isEqualTo("staff");
        assertThat(probe.any()).isEqualTo("any");
        assertThatThrownBy(probe::admin).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    void plainUserPassesOnlyTheAnyRoleRule() {
        assertThat(probe.any()).isEqualTo("any");
        assertThatThrownBy(probe::admin).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(probe::staff).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void anAuthorityWithoutTheRolePrefixIsNotARole() {
        assertThatThrownBy(probe::admin).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "SUPERUSER")
    void anUnknownRoleGrantsNothing() {
        assertThatThrownBy(probe::admin).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(probe::staff).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(probe::any).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noAuthenticationAtAllIsRejected() {
        assertThatThrownBy(probe::any).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
