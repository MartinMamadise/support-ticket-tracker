package za.co.mamadise.support_ticket_tracker;

import java.util.List;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import za.co.mamadise.support_ticket_tracker.team.Team;
import za.co.mamadise.support_ticket_tracker.team.TeamRepository;
import za.co.mamadise.support_ticket_tracker.user.AppUser;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.Role;
import za.co.mamadise.support_ticket_tracker.user.UserService;

/**
 * Test data for integration tests. All {@code @SpringBootTest} classes share one in-memory H2 database, so every class
 * starts with {@link #reset()}, which empties all tables in foreign-key order (the SLA policy seed rows are kept).
 * Accounts are created through {@link UserService}, i.e. with real BCrypt hashes, and all use {@link #PASSWORD}.
 * Import with {@code @Import(TestDatabase.class)}.
 */
@TestComponent
public class TestDatabase {

    public static final String PASSWORD = "Integration-Pass-1";
    public static final String ADMIN = "it.admin";

    private static final List<String> TABLES_CHILD_FIRST = List.of("notifications", "ticket_attachments",
            "ticket_assignments", "ticket_activity", "ticket_comments", "tickets", "team_members", "teams", "app_users");

    private final JdbcTemplate jdbc;
    private final UserService userService;
    private final AppUserRepository users;
    private final TeamRepository teams;

    public TestDatabase(JdbcTemplate jdbc, UserService userService, AppUserRepository users, TeamRepository teams) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.users = users;
        this.teams = teams;
    }

    public void reset() {
        for (String table : TABLES_CHILD_FIRST) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    public AppUser user(String username, Role role) {
        Long id = userService.createUser(username, username + "@example.com", PASSWORD, role).id();
        return users.findById(id).orElseThrow();
    }

    public AppUser admin() {
        return user(ADMIN, Role.ADMIN);
    }

    public Team team(String name, AppUser... members) {
        Team team = new Team(name, null);
        team.getMembers().addAll(Set.of(members));
        return teams.saveAndFlush(team);
    }
}
