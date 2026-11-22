package za.co.mamadise.support_ticket_tracker.sla;

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

import za.co.mamadise.support_ticket_tracker.auth.AdminOnly;
import za.co.mamadise.support_ticket_tracker.ticket.TicketPriority;

@RestController
@RequestMapping("/api/admin/sla-policies")
@AdminOnly
@Tag(name = "Administration")
public class SlaPolicyController {

    private final SlaService slaService;

    public SlaPolicyController(SlaService slaService) {
        this.slaService = slaService;
    }

    @GetMapping
    @Operation(summary = "SLA targets per priority")
    public List<SlaPolicyResponse> list() {
        return slaService.listPolicies();
    }

    @PutMapping("/{priority}")
    @Operation(summary = "Change the SLA targets of one priority",
            description = "Applies to deadlines computed from now on; existing deadlines are not rewritten.")
    @ApiResponse(responseCode = "400", description = "Invalid minutes, or resolution shorter than response")
    public SlaPolicyResponse update(@PathVariable TicketPriority priority, @Valid @RequestBody SlaPolicyRequest request) {
        return slaService.updatePolicy(priority, request);
    }
}
