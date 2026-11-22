package za.co.mamadise.support_ticket_tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;
import za.co.mamadise.support_ticket_tracker.user.Role;

/**
 * Verifies the migration-managed schema on the test database. The context itself only starts if Hibernate's
 * {@code ddl-auto=validate} accepts the schema the migrations produced, so a drift between entities and
 * migrations fails every {@code @SpringBootTest}; this class additionally checks what validation cannot see.
 */
@SpringBootTest
class DatabaseMigrationTest {

    private static final String INSERT_TICKET = "INSERT INTO tickets "
            + "(title, description, status, priority, created_at, updated_at, version) "
            + "VALUES ('Title', 'Description', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)";

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsAreAppliedInOrderAndNoneIsPending() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(Arrays.stream(applied).map(m -> m.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState))
                .allMatch(state -> state.isApplied() && !state.isFailed());
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void listIndexesExist() {
        List<String> indexes = jdbc.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE UPPER(TABLE_NAME) = 'TICKETS'",
                String.class);

        assertThat(indexes).map(String::toLowerCase).contains(
                "idx_tickets_created_at",
                "idx_tickets_status_created_at",
                "idx_tickets_priority_created_at",
                "idx_tickets_updated_at");
    }

    @Test
    void userTableHasUniqueUsernameAndEmailAndStoresNoDefaultAccount() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Long.class)).isZero();

        String insert = "INSERT INTO app_users (username, email, password_hash, enabled, created_at, updated_at) "
                + "VALUES (?, ?, 'hash-hash-hash-hash-hash-hash', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try {
            assertThat(jdbc.update(insert, "migration.user", "migration.user@example.com")).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update(insert, "migration.user", "other@example.com"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, "other.user", "migration.user@example.com"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM app_users WHERE username IN ('migration.user', 'other.user')");
        }
    }

    @Test
    void roleColumnDefaultsToTheLeastPrivilegedRoleAndRejectsUnknownRoles() {
        String insert = "INSERT INTO app_users (username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, 'hash-hash-hash-hash-hash-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        String insertWithRole = "INSERT INTO app_users (username, email, password_hash, role, created_at, updated_at) "
                + "VALUES (?, ?, 'hash-hash-hash-hash-hash-hash', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try {
            jdbc.update(insert, "role.default", "role.default@example.com");
            assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE username = 'role.default'", String.class))
                    .isEqualTo("USER");

            assertThatThrownBy(() -> jdbc.update(insertWithRole, "role.bogus", "role.bogus@example.com", "SUPERUSER"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            for (Role role : Role.values()) {
                // Derived from the enum: a new constant without a migration widening the CHECK fails here.
                assertThat(jdbc.update(insertWithRole, "role." + role.name().toLowerCase(),
                        "role." + role.name().toLowerCase() + "@example.com", role.name())).isEqualTo(1);
            }
        } finally {
            jdbc.update("DELETE FROM app_users WHERE username LIKE 'role.%'");
        }
    }

    @Test
    void databaseRejectsStatusOutsideTheEnum() {
        assertThatThrownBy(() -> jdbc.update(INSERT_TICKET, "BOGUS", "LOW"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsPriorityOutsideTheEnum() {
        assertThatThrownBy(() -> jdbc.update(INSERT_TICKET, "OPEN", "URGENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseAcceptsEveryKnownStatusAndPriority() {
        try {
            // Derived from the enums: adding a value without a migration that widens the constraint fails here.
            for (TicketStatus status : TicketStatus.values()) {
                assertThat(jdbc.update(INSERT_TICKET, status.name(), "MEDIUM")).isEqualTo(1);
            }
            for (TicketPriority priority : TicketPriority.values()) {
                assertThat(jdbc.update(INSERT_TICKET, "OPEN", priority.name())).isEqualTo(1);
            }
        } finally {
            jdbc.update("DELETE FROM tickets WHERE title = 'Title' AND description = 'Description'");
        }
    }
}
