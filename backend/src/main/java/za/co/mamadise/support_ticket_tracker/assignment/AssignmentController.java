package za.co.mamadise.support_ticket_tracker.assignment;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.StaffOnly;
import za.co.mamadise.support_ticket_tracker.ticket.TicketResponse;

@RestController
@RequestMapping("/api/tickets/{ticketId}")
@StaffOnly
@Tag(name = "Assignment", description = "Assign tickets to agents and teams (staff only)")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PutMapping("/assignment")
    @Operation(summary = "Assign or reassign a ticket",
            description = "Sets the assignee and team (null = none). The assignee must be an active agent or "
                    + "administrator and, when a team is given, a member of it; the team must be active.")
    @ApiResponse(responseCode = "200", description = "The updated ticket")
    @ApiResponse(responseCode = "400", description = "Unknown account/team, not staff, or not a member of the team")
    @ApiResponse(responseCode = "403", description = "No work access to this ticket")
    @ApiResponse(responseCode = "404", description = "No such ticket visible to the caller")
    @ApiResponse(responseCode = "409", description = "Final ticket, inactive team, deactivated agent, or stale version")
    public TicketResponse assign(@PathVariable Long ticketId, @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.assign(ticketId, request);
    }

    @GetMapping("/assignments")
    @Operation(summary = "Assignment history", description = "Oldest first.")
    public List<AssignmentResponse> history(@PathVariable Long ticketId) {
        return assignmentService.history(ticketId);
    }
}
