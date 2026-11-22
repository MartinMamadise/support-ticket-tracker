package za.co.mamadise.support_ticket_tracker.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Account creation against the real (H2, Flyway-migrated) schema. */
@SpringBootTest
@org.springframework.context.annotation.Import(za.co.mamadise.support_ticket_tracker.TestDatabase.class)
class UserServiceTest {

    private static final String PASSWORD = "correct horse 42";

    @Autowired
    private UserService userService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private za.co.mamadise.support_ticket_tracker.TestDatabase database;

    /** Full reset: tickets and teams of other test classes reference accounts. */
    @BeforeEach
    void clean() {
        database.reset();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanAfter() {
        database.reset();
    }

    @Test
    void passwordIsStoredOnlyAsABcryptHash() {
        UserResponse created = userService.createUser("Thandi", "Thandi@Example.com", PASSWORD);

        AppUser stored = users.findById(created.id()).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(stored.getPasswordHash()).startsWith("$2").hasSize(60);
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong password 1", stored.getPasswordHash())).isFalse();
        assertThat(stored.toString()).doesNotContain(stored.getPasswordHash());
    }

    @Test
    void samepasswordGetsADifferentHashForEachAccount() {
        userService.createUser("first.user", "first@example.com", PASSWORD);
        userService.createUser("second.user", "second@example.com", PASSWORD);

        assertThat(users.findByUsername("first.user").orElseThrow().getPasswordHash())
                .isNotEqualTo(users.findByUsername("second.user").orElseThrow().getPasswordHash());
    }

    @Test
    void usernameAndEmailAreNormalisedAndEnabledByDefault() {
        userService.createUser("  Thandi.M ", " Thandi@Example.COM ", PASSWORD);

        AppUser stored = users.findByUsername("thandi.m").orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("thandi@example.com");
        assertThat(stored.isEnabled()).isTrue();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void createdResponseHasNoPasswordInformation() {
        UserResponse created = userService.createUser("thandi", "thandi@example.com", PASSWORD);

        assertThat(created.toString()).doesNotContain(PASSWORD).doesNotContain("$2");
        assertThat(UserResponse.class.getRecordComponents()).extracting("name")
                .containsExactly("id", "username", "email", "fullName", "role", "enabled", "createdAt")
                .noneMatch(name -> name.toString().toLowerCase().contains("password"));
    }

    @Test
    void usernameAndEmailMustBeUniqueIgnoringCase() {
        userService.createUser("thandi", "thandi@example.com", PASSWORD);

        assertThatThrownBy(() -> userService.createUser("THANDI", "other@example.com", PASSWORD))
                .isInstanceOfSatisfying(DuplicateUserException.class,
                        e -> assertThat(e.getFieldErrors()).containsKey("username"));
        assertThatThrownBy(() -> userService.createUser("other", "THANDI@example.com", PASSWORD))
                .isInstanceOfSatisfying(DuplicateUserException.class,
                        e -> assertThat(e.getFieldErrors()).containsKey("email"));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void invalidDetailsAreReportedTogether() {
        assertThatThrownBy(() -> userService.createUser("a@b", "not-an-email", "short"))
                .isInstanceOfSatisfying(InvalidUserException.class,
                        e -> assertThat(e.getFieldErrors()).containsKeys("username", "email", "password"));
        assertThat(users.count()).isZero();
    }

    @org.junit.jupiter.api.AfterEach
    void removeAccounts() {
        users.deleteAll();
    }

    @Test
    void accountsAreOrdinaryUsersByDefaultAndTheRoleIsPersisted() {
        UserResponse created = userService.createUser("plain.user", "plain@example.com", PASSWORD);

        assertThat(created.role()).isEqualTo(Role.USER);
        assertThat(users.findById(created.id()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void anExplicitRoleIsPersistedAndLoadedForAuthentication(@Autowired AppUserDetailsService detailsService) {
        userService.createUser("agent.one", "agent.one@example.com", PASSWORD, Role.SUPPORT_AGENT);

        var details = detailsService.loadUserByUsername("agent.one");

        assertThat(details.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_SUPPORT_AGENT");
        assertThat(((AppUserPrincipal) detailsService.loadUserByUsername("AGENT.ONE@example.com")).getRole())
                .isEqualTo(Role.SUPPORT_AGENT);
    }

    @Test
    void changeRoleUpdatesAnotherAccountAndRefusesSelfChangeAndUnknownAccounts() {
        UserResponse boss = userService.createUser("the.boss", "boss@example.com", PASSWORD, Role.ADMIN);
        UserResponse other = userService.createUser("other.one", "other@example.com", PASSWORD);

        assertThat(userService.changeRole(boss.id(), other.id(), Role.SUPPORT_AGENT).role())
                .isEqualTo(Role.SUPPORT_AGENT);
        assertThat(users.findById(other.id()).orElseThrow().getRole()).isEqualTo(Role.SUPPORT_AGENT);

        assertThatThrownBy(() -> userService.changeRole(boss.id(), boss.id(), Role.USER))
                .isInstanceOf(RoleChangeNotAllowedException.class);
        assertThat(users.findById(boss.id()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThatThrownBy(() -> userService.changeRole(boss.id(), 999_999L, Role.USER))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void listUsersReturnsEveryAccountWithItsRoleOldestFirst() {
        userService.createUser("first.one", "first.one@example.com", PASSWORD, Role.ADMIN);
        userService.createUser("second.one", "second.one@example.com", PASSWORD);

        assertThat(userService.listUsers()).extracting(UserResponse::username, UserResponse::role)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("first.one", Role.ADMIN),
                        org.assertj.core.groups.Tuple.tuple("second.one", Role.USER));
    }
}
