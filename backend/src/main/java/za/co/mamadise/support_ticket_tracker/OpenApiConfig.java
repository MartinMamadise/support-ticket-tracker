package za.co.mamadise.support_ticket_tracker;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title and general description of the generated OpenAPI document. Endpoint details are declared with
 * annotations on {@code TicketController} and the DTO records. The document and Swagger UI are only served when
 * {@code springdoc.api-docs.enabled} / {@code springdoc.swagger-ui.enabled} are true (the "dev" profile).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI supportHubOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("sessionCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")))
                .addSecurityItem(new SecurityRequirement().addList("sessionCookie"))
                .info(new Info()
                .title("SupportHub Ticket API")
                .version("v1")
                .description("""
                        REST API of the SupportHub support ticket tracker.

                        **Authentication:** session cookie. `POST /api/auth/login` starts a session (HttpOnly `JSESSIONID` \
                        cookie); every other endpoint except `GET /api/auth/csrf` needs it and answers 401 without \
                        it. POST, PUT and DELETE also need the CSRF token from the `XSRF-TOKEN` cookie in the \
                        `X-XSRF-TOKEN` header (403 otherwise).

                        **Timestamps** are UTC instants in ISO-8601 format, e.g. `2026-11-21T12:38:00.123Z`.

                        **Errors** are always returned as JSON in the `ApiError` shape. Validation problems list one \
                        message per field in `fieldErrors`.

                        **Concurrency:** every ticket carries a `version`. Send it back on update to detect edits made \
                        by someone else (HTTP 409).
                        """));
    }
}
