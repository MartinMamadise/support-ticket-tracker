package za.co.mamadise.support_ticket_tracker.team;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.StaffOnly;

/** Read access to teams for support staff (assignment pickers, team queues). Changes: {@link AdminTeamController}. */
@RestController
@RequestMapping("/api/teams")
@StaffOnly
@Tag(name = "Teams", description = "Support teams (staff only)")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @Operation(summary = "List teams", description = "Active teams by default; `includeInactive=true` adds inactive ones.")
    @ApiResponse(responseCode = "200", description = "Teams with members and active/unassigned ticket counts")
    @ApiResponse(responseCode = "403", description = "Not ADMIN or SUPPORT_AGENT")
    public List<TeamResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return teamService.listTeams(includeInactive);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a team")
    @ApiResponse(responseCode = "200", description = "The team")
    @ApiResponse(responseCode = "404", description = "No such team")
    public TeamResponse get(@PathVariable Long id) {
        return teamService.getTeam(id);
    }
}
