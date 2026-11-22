package za.co.mamadise.support_ticket_tracker.notification;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AnyRole;
import za.co.mamadise.support_ticket_tracker.ticket.PageResponse;

@RestController
@RequestMapping("/api/notifications")
@AnyRole
@Tag(name = "Notifications", description = "The signed-in account's own in-app notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List my notifications", description = "Newest first. Page size at most 100.")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.list(page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    @ApiResponse(responseCode = "404", description = "No such notification for this account")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead());
    }
}
