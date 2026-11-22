package za.co.mamadise.support_ticket_tracker.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.error.ApiError;
import za.co.mamadise.support_ticket_tracker.user.AppUserPrincipal;
import za.co.mamadise.support_ticket_tracker.user.AppUserRepository;
import za.co.mamadise.support_ticket_tracker.user.UserResponse;
import za.co.mamadise.support_ticket_tracker.user.UserService;

/** Login, current user and CSRF bootstrap. Logout is {@code POST /api/auth/logout}, handled by Spring Security. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Session login, current user and logout (POST /api/auth/logout, 204)")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** One message for every failure (unknown user, wrong password, disabled account) so nothing is revealed. */
    static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AppUserRepository users;
    private final LoginAttemptLimiter limiter;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, AppUserRepository users, LoginAttemptLimiter limiter) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
        this.limiter = limiter;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Sign in",
            description = "Authenticates with username (or email) and password and starts a session (HttpOnly "
                    + "JSESSIONID cookie). Requires the X-XSRF-TOKEN header: call GET /api/auth/csrf first.")
    @ApiResponse(responseCode = "200", description = "Signed in; the account is returned (no password or hash)")
    @ApiResponse(responseCode = "400", description = "Missing or oversized fields",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials or disabled account (same answer for both)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Missing or invalid CSRF token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many failed attempts for this name; wait and retry",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        String key = UserService.normalise(body.username());
        if (limiter.isBlocked(key)) {
            log.warn("Login refused: too many recent failures for one name");
            throw new TooManyAttemptsException("Too many failed sign-in attempts. Please wait a few minutes and try again.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
                    .unauthenticated(key, body.password()));
        } catch (InternalAuthenticationServiceException ex) {
            throw ex; // a backend fault (e.g. database down) is a 500, not a "wrong password"
        } catch (AuthenticationException ex) {
            limiter.recordFailure(key);
            log.warn("Failed login attempt ({})", ex.getClass().getSimpleName());
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        limiter.recordSuccess(key);

        // Session fixation protection: a session id that existed before login is never reused afterwards.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        AppUserPrincipal signedIn = (AppUserPrincipal) authentication.getPrincipal();
        return users.findById(signedIn.getId()).map(UserResponse::from)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS));
    }

    @GetMapping("/me")
    @Operation(summary = "Current user",
            description = "Returns the signed-in account. 401 when there is no session, it has expired, or the "
                    + "account was removed or disabled since sign-in (the session is then ended).")
    @ApiResponse(responseCode = "200", description = "The signed-in account")
    @ApiResponse(responseCode = "401", description = "Not signed in or session no longer valid",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal, HttpServletRequest request) {
        if (principal != null) {
            var current = users.findById(principal.getId()).filter(user -> user.isEnabled());
            if (current.isPresent()) {
                return UserResponse.from(current.get());
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
        throw new AuthenticationFailedException("Session is no longer valid");
    }

    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Obtain a CSRF token",
            description = "Sets the XSRF-TOKEN cookie. Send its value in the X-XSRF-TOKEN header on every POST, PUT "
                    + "and DELETE (Angular's HttpClient does this automatically). Call it before login and after logout.")
    @ApiResponse(responseCode = "204", description = "Cookie set; no body")
    public void csrf() {
        // The cookie is written by CsrfCookieFilter; nothing else to do.
    }
}
