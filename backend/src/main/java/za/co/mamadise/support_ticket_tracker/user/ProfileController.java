package za.co.mamadise.support_ticket_tracker.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AnyRole;
import za.co.mamadise.support_ticket_tracker.auth.CurrentUser;

/** The signed-in account's own profile. The account id always comes from the session, never from the request. */
@RestController
@RequestMapping("/api/profile")
@AnyRole
@Tag(name = "Profile", description = "Own account details and password")
public class ProfileController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public ProfileController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "My profile")
    public UserResponse get() {
        return userService.profile(currentUser.require().id());
    }

    @PutMapping
    @Operation(summary = "Update my name and email")
    @ApiResponse(responseCode = "400", description = "Invalid email or name")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    public UserResponse update(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(currentUser.require().id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change my password", description = "Requires the current password.")
    @ApiResponse(responseCode = "204", description = "Changed")
    @ApiResponse(responseCode = "400", description = "Wrong current password, or new password does not meet the policy")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changeOwnPassword(currentUser.require().id(), request);
        return ResponseEntity.noContent().build();
    }
}
