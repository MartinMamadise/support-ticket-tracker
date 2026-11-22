package za.co.mamadise.support_ticket_tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;
import za.co.mamadise.support_ticket_tracker.ticket.TicketSortField;
import za.co.mamadise.support_ticket_tracker.ticket.TicketStatus;

/** Checks the generated OpenAPI document describes the real API, so documentation cannot silently drift. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final String LIST = "$.paths['/api/tickets'].get";

    @Autowired
    private MockMvc mockMvc;

    private String apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void documentHasTitleAndAllTicketOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SupportHub Ticket API"))
                .andExpect(jsonPath("$.paths['/api/tickets'].get").exists())
                .andExpect(jsonPath("$.paths['/api/tickets'].post").exists())
                .andExpect(jsonPath("$.paths['/api/tickets/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/tickets/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/tickets/{id}'].delete").exists());
    }

    @Test
    void responseCodesAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(LIST + ".responses['200']").exists())
                .andExpect(jsonPath(LIST + ".responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/tickets'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/tickets'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/tickets'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/tickets/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/tickets/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/tickets/{id}'].delete.responses['204']").exists());
    }

    @Test
    void listParametersCoverPagingFilteringSortingAndSearch() throws Exception {
        String docs = apiDocs();

        List<String> names = JsonPath.read(docs, LIST + ".parameters[*].name");
        assertThat(names).containsExactlyInAnyOrder(
                "page", "size", "sortBy", "direction", "status", "priority", "createdFrom", "createdTo", "q");
    }

    @Test
    void documentedEnumValuesMatchTheCode() throws Exception {
        String docs = apiDocs();

        List<String> sortFields = JsonPath.read(docs, LIST + ".parameters[?(@.name=='sortBy')].schema.enum[*]");
        assertThat(sortFields).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(TicketSortField.values()).map(TicketSortField::apiName).toList());

        Map<String, Object> schemas = JsonPath.read(docs, "$.components.schemas");
        assertThat(enumValues(schemas, "TicketStatus"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(TicketStatus.values()).map(Enum::name).toList());
        assertThat(enumValues(schemas, "TicketPriority"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(TicketPriority.values()).map(Enum::name).toList());
    }

    @Test
    void dtoSchemasAreDocumented() throws Exception {
        String docs = apiDocs();

        Map<String, Object> schemas = JsonPath.read(docs, "$.components.schemas");
        assertThat(schemas).containsKeys("TicketRequest", "TicketResponse", "ApiError");
        assertThat(schemas.keySet()).anyMatch(name -> name.startsWith("PageResponse"));

        List<String> required = JsonPath.read(docs, "$.components.schemas.TicketRequest.required");
        assertThat(required).contains("title", "description", "priority");
        Map<String, Object> responseFields = JsonPath.read(docs, "$.components.schemas.TicketResponse.properties");
        assertThat(responseFields).containsKeys("id", "status", "createdAt", "resolvedAt", "version", "allowedTransitions");
    }

    @Test
    void sessionCookieAuthenticationAndAuthEndpointsAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"))
                .andExpect(jsonPath("$.security[0].sessionCookie").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/csrf'].get").exists());
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumValues(Map<String, Object> schemas, String schemaName) {
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        assertThat(schema).as("schema %s", schemaName).isNotNull();
        return (List<String>) schema.get("enum");
    }
}
