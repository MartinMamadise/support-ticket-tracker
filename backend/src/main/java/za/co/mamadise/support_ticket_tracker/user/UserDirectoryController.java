package za.co.mamadise.support_ticket_tracker.user;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.StaffOnly;

/** Lookups support staff need for assignment. Returns only id, username and display name (no email). */
@RestController
@RequestMapping("/api/users")
@StaffOnly
@Tag(name = "Users", description = "Account lookups for support staff")
public class UserDirectoryController {

    private final UserService userService;

    public UserDirectoryController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/assignable")
    @Operation(summary = "Accounts that can be assigned tickets",
            description = "Active ADMIN and SUPPORT_AGENT accounts; with `teamId`, only members of that team.")
    public List<UserSummary> assignable(@RequestParam(required = false) Long teamId) {
        return userService.assignableStaff(teamId);
    }
}
