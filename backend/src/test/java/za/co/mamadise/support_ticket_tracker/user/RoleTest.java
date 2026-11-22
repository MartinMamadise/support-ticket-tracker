package za.co.mamadise.support_ticket_tracker.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import za.co.mamadise.support_ticket_tracker.auth.Roles;

/** The role definition and its mapping to Spring Security authorities (no Spring context needed). */
class RoleTest {

    @Test
    void exactlyTheThreeDocumentedRolesExist() {
        assertThat(Role.values()).containsExactly(Role.ADMIN, Role.SUPPORT_AGENT, Role.USER);
    }

    @Test
    void eachRoleMapsToOneAuthorityPrefixedWithRole() {
        assertThat(Role.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
        assertThat(Role.SUPPORT_AGENT.authority()).isEqualTo("ROLE_SUPPORT_AGENT");
        assertThat(Role.USER.authority()).isEqualTo("ROLE_USER");
        assertThat(Role.ADMIN.toGrantedAuthority().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void unknownRoleNamesAreRejected() {
        for (String bogus : List.of("SUPERUSER", "admin", "ROLE_ADMIN", "", "ROOT")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> Role.valueOf(bogus))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void newAccountsAreOrdinaryUsersUnlessARoleIsGivenExplicitly() {
        assertThat(new AppUser("someone", "someone@example.com", "hash-hash-hash-hash-hash").getRole())
                .isEqualTo(Role.USER);
        assertThat(new AppUser("boss", "boss@example.com", "hash-hash-hash-hash-hash", Role.ADMIN).getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void anAccountCannotHaveNoRole() {
        AppUser user = new AppUser("someone", "someone@example.com", "hash-hash-hash-hash-hash");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> user.setRole(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void principalCarriesExactlyTheAuthorityOfItsRole() {
        for (Role role : Role.values()) {
            AppUserPrincipal principal = new AppUserPrincipal(new AppUser("someone", "someone@example.com",
                    "hash-hash-hash-hash-hash", role));

            assertThat(principal.getRole()).isEqualTo(role);
            assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactly(role.authority());
        }
    }

    @Test
    void principalToStringShowsTheRoleButNeverTheHash() {
        AppUserPrincipal principal = new AppUserPrincipal(
                new AppUser("someone", "someone@example.com", "secret-hash-secret-hash", Role.SUPPORT_AGENT));

        assertThat(principal.toString()).contains("SUPPORT_AGENT").doesNotContain("secret-hash");
        principal.eraseCredentials();
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities()).hasSize(1); // erasing credentials keeps the role
    }

    @Test
    void userResponseExposesTheRoleOnly() {
        AppUser user = new AppUser("agent", "agent@example.com", "secret-hash-secret-hash", Role.SUPPORT_AGENT);

        UserResponse response = UserResponse.from(user);

        assertThat(response.role()).isEqualTo(Role.SUPPORT_AGENT);
        assertThat(Arrays.stream(UserResponse.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "username", "email", "role");
    }

    @Test
    void securityExpressionsNameOnlyRealRoles() {
        List<String> valid = Arrays.stream(Role.values()).map(Role::name).toList();
        for (String expression : List.of(Roles.ADMIN, Roles.STAFF, Roles.ANY)) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(expression);
            int found = 0;
            while (m.find()) {
                assertThat(valid).contains(m.group(1));
                found++;
            }
            assertThat(found).isPositive();
        }
        assertThat(Roles.ADMIN).isEqualTo("hasRole('ADMIN')");
        assertThat(Roles.STAFF).doesNotContain("'USER'");
        assertThat(Roles.ANY).contains("'USER'");
    }
}
