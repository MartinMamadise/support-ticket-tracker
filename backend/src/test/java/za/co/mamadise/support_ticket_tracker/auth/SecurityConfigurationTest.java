package za.co.mamadise.support_ticket_tracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** What is protected, what is public, and how CSRF and CORS behave. Security is fully on: nothing is disabled here. */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(za.co.mamadise.support_ticket_tracker.TestDatabase.class)
class SecurityConfigurationTest {

    private static final String TICKET = "{\"title\":\"VPN down\",\"description\":\"Cannot connect\",\"priority\":\"HIGH\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private za.co.mamadise.support_ticket_tracker.TestDatabase database;

    /** user("someone") must be a real account: the ticket services resolve the caller from the database (fail closed). */
    @org.junit.jupiter.api.BeforeEach
    void createAccount() {
        database.reset();
        database.user("someone", za.co.mamadise.support_ticket_tracker.user.Role.USER);
    }

    /** All Spring tests share one H2 database: leave it empty for the next class. */
    @org.junit.jupiter.api.AfterEach
    void resetAfter() {
        database.reset();
    }

    @Test
    void everyTicketEndpointRejectsAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TICKET))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/tickets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TICKET))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/tickets/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathsAreProtectedToo() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectionIsJsonWithoutDetailsOrABasicAuthChallenge() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at za.co"))));
    }

    // Fresh application context: spring-security-test's csrf() helper, used by other tests sharing the cached context,
    // replaces the CSRF token repository for the rest of that context, which hides the XSRF-TOKEN cookie.
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test
    void rejectedAnonymousRequestDoesNotCreateASession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void anAuthenticatedCallerReachesTheTicketApi() throws Exception {
        mockMvc.perform(get("/api/tickets").with(user("someone"))).andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestsNeedACsrfTokenEvenWhenSignedIn() throws Exception {
        mockMvc.perform(post("/api/tickets").with(user("someone")).contentType(MediaType.APPLICATION_JSON)
                        .content(TICKET))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token"));
        mockMvc.perform(put("/api/tickets/1").with(user("someone")).contentType(MediaType.APPLICATION_JSON)
                        .content(TICKET))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tickets/1").with(user("someone"))).andExpect(status().isForbidden());
    }

    @Test
    void aWrongCsrfHeaderIsRejectedAndSafeMethodsNeedNone() throws Exception {
        mockMvc.perform(post("/api/tickets").with(user("someone")).header("X-XSRF-TOKEN", "forged-value")
                        .cookie(new Cookie("XSRF-TOKEN", "a-different-value"))
                        .contentType(MediaType.APPLICATION_JSON).content(TICKET))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets").with(user("someone"))).andExpect(status().isOk());
    }

    @Test
    void loginItselfIsCsrfProtected() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token"));
    }

    // Fresh application context: spring-security-test's csrf() helper, used by other tests sharing the cached context,
    // replaces the CSRF token repository for the rest of that context, which hides the XSRF-TOKEN cookie.
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test
    void csrfEndpointIsPublicAndSetsAScriptReadableCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    // Fresh application context: spring-security-test's csrf() helper, used by other tests sharing the cached context,
    // replaces the CSRF token repository for the rest of that context, which hides the XSRF-TOKEN cookie.
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test
    void anyResponseCarriesTheCsrfCookieSoTheClientCanBootstrapFromAnyCall() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    // Fresh application context: spring-security-test's csrf() helper, used by other tests sharing the cached context,
    // replaces the CSRF token repository for the rest of that context, which hides the XSRF-TOKEN cookie.
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test
    void cookieTokenFromTheCsrfEndpointIsAcceptedInTheHeader() throws Exception {
        Cookie token = mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();

        // Valid token, but no credentials: the CSRF check passes and authentication then rejects (401, not 403).
        mockMvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong password 1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyTheConfiguredAngularOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/auth/login").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void publicDocumentationEndpointsAreNotBehindLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void passwordEncoderProducesSaltedBcryptHashes() {
        String first = passwordEncoder.encode("correct horse 42");
        String second = passwordEncoder.encode("correct horse 42");

        assertThat(first).startsWith("$2").isNotEqualTo(second).doesNotContain("correct horse");
        assertThat(passwordEncoder.matches("correct horse 42", first)).isTrue();
    }
}
