package za.co.mamadise.support_ticket_tracker.activity;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AnyRole;

@RestController
@RequestMapping("/api/tickets/{ticketId}/activity")
@AnyRole
@Tag(name = "Ticket activity", description = "Change history of a ticket")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    @Operation(summary = "Activity timeline",
            description = "Oldest first. Entries about internal notes are only returned to staff who work on the ticket.")
    @ApiResponse(responseCode = "200", description = "The timeline")
    @ApiResponse(responseCode = "404", description = "No such ticket, or not visible to the caller")
    public List<ActivityResponse> list(@PathVariable Long ticketId) {
        return activityService.list(ticketId);
    }
}
