package za.co.mamadise.support_ticket_tracker.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AnyRole;
import za.co.mamadise.support_ticket_tracker.auth.StaffOnly;

@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard", description = "Statistics computed by the server")
public class DashboardController {

    private final DashboardService dashboardService;
    private final WorkloadService workloadService;

    public DashboardController(DashboardService dashboardService, WorkloadService workloadService) {
        this.dashboardService = dashboardService;
        this.workloadService = workloadService;
    }

    @GetMapping("/dashboard")
    @AnyRole
    @Operation(summary = "Dashboard statistics",
            description = "Over the tickets visible to the caller. `trendDays` 1-90 (default 14). Workload is included for staff only.")
    public DashboardResponse dashboard(@RequestParam(defaultValue = "14") int trendDays) {
        return dashboardService.dashboard(trendDays);
    }

    @GetMapping("/workload")
    @StaffOnly
    @Operation(summary = "Agent and team workload (staff only)")
    @ApiResponse(responseCode = "403", description = "Not ADMIN or SUPPORT_AGENT")
    public WorkloadResponse workload() {
        return workloadService.workload();
    }
}
