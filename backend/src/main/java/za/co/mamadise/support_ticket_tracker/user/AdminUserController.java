package za.co.mamadise.support_ticket_tracker.user;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AdminOnly;
import za.co.mamadise.support_ticket_tracker.auth.AuthenticationFailedException;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;
import za.co.mamadise.support_ticket_tracker.ticket.PageResponse;

/**
 * Administrator-only account endpoints. Protected twice: the URL rule for {@code /api/admin/**} in SecurityConfig and
 * {@link AdminOnly} here. These are the only endpoints through which a role can be set or changed.
 */
@RestController
@RequestMapping("/api/admin/users")
@AdminOnly
@Tag(name = "Administration", description = "Administrator-only user, team and SLA management")
public class AdminUserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public AdminUserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "Paged, ordered by username. Never contains passwords or hashes.")
    @ApiResponse(responseCode = "200", description = "One page of accounts")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "403", description = "Signed in without the ADMIN role")
    public PageResponse<UserResponse> list(
            @Parameter(description = "Text in username, email or full name") @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.searchUsers(q, role, enabled, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account")
    @ApiResponse(responseCode = "404", description = "No such account")
    public UserResponse get(@PathVariable Long id) {
        return userService.profile(id);
    }

    @PostMapping
    @Operation(summary = "Create an account", description = "The role defaults to USER when omitted.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Invalid username, email, password or role")
    @ApiResponse(responseCode = "409", description = "Username or email already in use")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody AdminUserCreateRequest request) {
        UserResponse created = userService.createByAdmin(request);
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an account",
            description = "Email, full name, role and activation. Own role and own activation cannot be changed (409). "
                    + "Deactivating an agent removes them from their active tickets; their sessions end on the next request.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "No such account")
    @ApiResponse(responseCode = "409", description = "Email in use, or change to own role/activation")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return userService.updateByAdmin(currentUser.require().id(), id, request);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Set a new password for an account")
    @ApiResponse(responseCode = "204", description = "Changed")
    @ApiResponse(responseCode = "400", description = "Password does not meet the policy")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Change an account's role",
            description = "Administrators only. An administrator cannot change their own role. The change applies "
                    + "to the target's existing sessions on their next request.")
    @ApiResponse(responseCode = "200", description = "The updated account")
    @ApiResponse(responseCode = "400", description = "Missing or unknown role")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "403", description = "Signed in without the ADMIN role, or missing CSRF token")
    @ApiResponse(responseCode = "404", description = "No such account")
    @ApiResponse(responseCode = "409", description = "Own role cannot be changed")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest body,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        if (actor == null) {
            throw new AuthenticationFailedException("Authentication required"); // fail closed
        }
        return userService.changeRole(actor.getId(), id, body.role());
    }
}
