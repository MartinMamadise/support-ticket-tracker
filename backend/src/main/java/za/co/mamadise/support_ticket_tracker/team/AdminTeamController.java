package za.co.mamadise.support_ticket_tracker.team;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AdminOnly;

/** Team administration. Protected by the /api/admin/** URL rule and {@link AdminOnly}. Teams are deactivated, not deleted. */
@RestController
@RequestMapping("/api/admin/teams")
@AdminOnly
@Tag(name = "Administration")
public class AdminTeamController {

    private final TeamService teamService;

    public AdminTeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @Operation(summary = "Create a team")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Name already in use")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamRequest request) {
        TeamResponse created = teamService.createTeam(request);
        return ResponseEntity.created(URI.create("/api/teams/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit or (de)activate a team")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "No such team")
    @ApiResponse(responseCode = "409", description = "Name already in use")
    public TeamResponse update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return teamService.updateTeam(id, request);
    }

    @PutMapping("/{id}/members")
    @Operation(summary = "Replace a team's members", description = "Members must be active ADMIN or SUPPORT_AGENT accounts.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Unknown or non-staff account in the list")
    @ApiResponse(responseCode = "404", description = "No such team")
    public TeamResponse setMembers(@PathVariable Long id, @Valid @RequestBody TeamMembersRequest request) {
        return teamService.setMembers(id, request);
    }
}
