package za.co.mamadise.support_ticket_tracker.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** The opt-in first account gets the configured role; an invalid role stops startup instead of guessing. */
class BootstrapUserRunnerTest {

    private final UserService userService = mock(UserService.class);
    private final AppUserRepository users = mock(AppUserRepository.class);

    @Test
    void firstAccountIsAnAdministratorByDefaultBecauseItIsOptInAndEnvironmentOnly() {
        when(users.count()).thenReturn(0L);
        when(userService.createUser(any(), any(), any(), any()))
                .thenReturn(new UserResponse(1L, "first", "first@example.com", null, Role.ADMIN, true, null));

        new BootstrapUserRunner(userService, users, "first", "first@example.com", "correct horse 42", "ADMIN")
                .run(null);

        verify(userService).createUser("first", "first@example.com", "correct horse 42", Role.ADMIN);
    }

    @Test
    void anUnprivilegedFirstAccountCanBeRequested() {
        when(users.count()).thenReturn(0L);
        when(userService.createUser(any(), any(), any(), any()))
                .thenReturn(new UserResponse(1L, "first", "first@example.com", null, Role.USER, true, null));

        new BootstrapUserRunner(userService, users, "first", "first@example.com", "correct horse 42", " user ")
                .run(null);

        verify(userService).createUser(eq("first"), eq("first@example.com"), eq("correct horse 42"), eq(Role.USER));
    }

    @Test
    void nothingHappensOnceAnyAccountExists() {
        when(users.count()).thenReturn(3L);

        new BootstrapUserRunner(userService, users, "first", "first@example.com", "correct horse 42", "ADMIN")
                .run(null);

        verify(userService, never()).createUser(any(), any(), any(), any());
        verify(userService, never()).changeRole(any(), any(), any());
    }

    @Test
    void anInvalidRoleRefusesToStart() {
        assertThatThrownBy(() -> new BootstrapUserRunner(userService, users, "a", "a@example.com", "pw", "ROOT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("pw"));
    }
}
