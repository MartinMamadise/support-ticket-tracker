package za.co.mamadise.support_ticket_tracker.sla;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New SLA targets for one priority, in minutes (calendar time)")
public record SlaPolicyRequest(
        @NotNull(message = "responseMinutes is required")
        @Min(value = 1, message = "responseMinutes must be at least 1")
        @Max(value = 43200, message = "responseMinutes must be at most 43200 (30 days)")
        Integer responseMinutes,
        @NotNull(message = "resolutionMinutes is required")
        @Min(value = 1, message = "resolutionMinutes must be at least 1")
        @Max(value = 525600, message = "resolutionMinutes must be at most 525600 (365 days)")
        Integer resolutionMinutes) {
}
