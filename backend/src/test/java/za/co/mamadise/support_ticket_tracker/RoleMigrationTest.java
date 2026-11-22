package za.co.mamadise.support_ticket_tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs the real migrations against a separate in-memory database in two steps (up to V4, then to the latest) to prove
 * that accounts created before roles existed survive V5 untouched and become ordinary users, never administrators.
 */
class RoleMigrationTest {

    private static final String URL = "jdbc:h2:mem:role_migration;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @Test
    void existingAccountsKeepTheirDataAndBecomeOrdinaryUsers() {
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target("4").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, "sa", ""));
        for (String name : List.of("legacy.one", "legacy.two", "legacy.three")) {
            jdbc.update("INSERT INTO app_users (username, email, password_hash, enabled, created_at, updated_at) "
                    + "VALUES (?, ?, 'legacy-hash-legacy-hash-legacy', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    name, name + "@example.com");
        }
        jdbc.update("UPDATE app_users SET enabled = FALSE WHERE username = 'legacy.two'");

        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForList("SELECT username FROM app_users ORDER BY id", String.class))
                .containsExactly("legacy.one", "legacy.two", "legacy.three");
        assertThat(jdbc.queryForList("SELECT DISTINCT role FROM app_users", String.class)).containsExactly("USER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE role = 'ADMIN'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled FROM app_users WHERE username = 'legacy.two'", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE username = 'legacy.one'",
                String.class)).isEqualTo("legacy-hash-legacy-hash-legacy");
    }
}
