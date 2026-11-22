package za.co.mamadise.support_ticket_tracker.auth;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;

/**
 * The one authentication mechanism: a server-side HTTP session identified by an HttpOnly cookie, established by
 * {@code POST /api/auth/login}. State-changing requests are protected by CSRF tokens (XSRF-TOKEN cookie,
 * X-XSRF-TOKEN header: Angular's default). Everything except the few endpoints listed below needs a signed-in user; roles ({@code user.Role}) become
 * authorities {@code ROLE_*} and are enforced with URL rules plus method security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @AdminOnly / @StaffOnly / @AnyRole (see Roles)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    /** Brute-force protection for POST /api/auth/login (see LoginAttemptLimiter). */
    @Bean
    LoginAttemptLimiter loginAttemptLimiter(@Value("${app.security.login.max-failures:5}") int maxFailures,
            @Value("${app.security.login.window-minutes:15}") long windowMinutes, java.time.Clock clock) {
        return new LoginAttemptLimiter(maxFailures, java.time.Duration.ofMinutes(windowMinutes), clock);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        // Unknown user and wrong password stay indistinguishable (the default): both end as BadCredentialsException.
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, CorsConfigurationSource corsConfigurationSource, AppUserRepository users,
            @Value("${app.security.secure-cookies:true}") boolean secureCookies) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax").path("/"));
        SecurityErrorHandlers errors = new SecurityErrorHandlers();

        http
                .authenticationManager(authenticationManager)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Reload the account on each request so role changes, disabling and deletion apply to live sessions.
                .addFilterAfter(new SessionRoleRefreshFilter(users, securityContextRepository), SecurityContextHolderFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Do not remember rejected requests: that would create a session for anonymous callers.
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .authorizeHttpRequests(auth -> auth
                        // The only public endpoints. Logout is handled by the logout filter before this point.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        // Spring's error dispatch, so a failure is reported as itself instead of turning into a 401.
                        .requestMatchers("/error").permitAll()
                        // Liveness/readiness for Docker and load balancers: status only, no details are exposed.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // API documentation: only served at all when the dev profile enables springdoc.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Administration: ADMIN only (AdminUserController repeats this with @AdminOnly).
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Any other actuator endpoint stays closed even if exposed by mistake.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Tickets and everything else: any signed-in account. Finer rules are method-level
                        // (@AdminOnly, @StaffOnly, @AnyRole) and, for tickets, ownership/team checks in the services
                        // (TicketAccessPolicy).
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Allows the listed origins (comma separated) to call the API with cookies. Empty = same-origin only. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins); // exact origins only: credentials are allowed, so never "*"
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
