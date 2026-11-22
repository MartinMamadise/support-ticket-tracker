package za.co.mamadise.support_ticket_tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Guards the production configuration. The test classpath uses its own application.properties, so the real one
 * is read from the source tree (Maven runs tests with the backend directory as working directory).
 */
class MainConfigurationTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static Properties load(String fileName) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve(fileName))) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void productionNeverLetsHibernateChangeTheSchema() throws IOException {
        Properties main = load("application.properties");

        assertThat(main.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(main.getProperty("spring.flyway.enabled", "true")).isEqualTo("true");
        assertThat(main.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("${FLYWAY_BASELINE_ON_MIGRATE:false}");
    }

    @Test
    void secretsComeFromEnvironmentVariables() throws IOException {
        Properties main = load("application.properties");

        assertThat(main.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(main.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(main.getProperty("spring.datasource.url")).startsWith("jdbc:mysql://${DB_HOST");
    }

    @Test
    void apiDocumentationIsOffByDefaultAndOnInTheDevProfile() throws IOException {
        Properties main = load("application.properties");
        Properties dev = load("application-dev.properties");

        assertThat(main.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(main.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(dev.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        assertThat(dev.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
    }

    @Test
    void cookiesAreSecureByDefaultAndDevelopmentRelaxesOnlyThat() throws IOException {
        Properties main = load("application.properties");
        Properties dev = load("application-dev.properties");

        assertThat(main.getProperty("server.servlet.session.cookie.secure")).isEqualTo("${SESSION_COOKIE_SECURE:true}");
        assertThat(main.getProperty("app.security.secure-cookies")).isEqualTo("${SESSION_COOKIE_SECURE:true}");
        assertThat(main.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(main.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(dev.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
        assertThat(dev.getProperty("app.security.secure-cookies")).isEqualTo("false");
    }

    @Test
    void productionHasNoBuiltInAccountAndNoCorsOriginByDefault() throws IOException {
        Properties main = load("application.properties");

        assertThat(main.getProperty("app.bootstrap-user.enabled")).isEqualTo("${BOOTSTRAP_USER_ENABLED:false}");
        assertThat(main.getProperty("app.bootstrap-user.password")).isEqualTo("${BOOTSTRAP_USER_PASSWORD:}");
        assertThat(main.getProperty("app.bootstrap-user.role")).isEqualTo("${BOOTSTRAP_USER_ROLE:ADMIN}");
        assertThat(main.getProperty("app.cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS:}");
        assertThat(main.getProperty("app.security.bcrypt-strength")).isEqualTo("12");
    }
}
