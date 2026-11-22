package za.co.mamadise.support_ticket_tracker.ticket;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AdminOnly;
import za.co.mamadise.support_ticket_tracker.auth.AnyRole;
import za.co.mamadise.support_ticket_tracker.error.ApiError;

@RestController
@RequestMapping("/api/tickets")
@AnyRole
@Tag(name = "Tickets", description = "Create, read, update, delete and search support tickets. "
        + "Visibility and permissions depend on the caller (see TicketAccessPolicy).")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Lists tickets one page at a time. All parameters are optional; see {@link TicketListQuery}
     * for their meaning and defaults. Invalid values are answered with 400 and a per-parameter message.
     */
    @GetMapping
    @Operation(summary = "List tickets",
            description = "Returns one page of the tickets the caller may see (USER: own tickets; SUPPORT_AGENT: assigned, "
                    + "team and general-queue tickets plus their own; ADMIN: all). Filters are combined with AND (several values of one filter "
                    + "are combined with OR). The default order is newest first; `id` is always used as the final "
                    + "tie-breaker so pages are stable. A page past the end returns an empty `content`.")
    @ApiResponse(responseCode = "200", description = "A page of tickets (empty `content` when nothing matches)")
    @ApiResponse(responseCode = "400", description = "One or more query parameters are invalid; `fieldErrors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = """
                            {"timestamp":"2026-11-21T12:38:00.123Z","status":400,"error":"Bad Request",
                             "message":"Invalid query parameters",
                             "fieldErrors":{"size":"size must be 1 or greater"}}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public PageResponse<TicketResponse> listTickets(
            @Parameter(description = "Zero-based page number.",
                    schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) String page,
            @Parameter(description = "Page size. Values above 100 are reduced to 100; the response reports the size used.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(required = false) String size,
            @Parameter(description = "Sort field (case-insensitive). `priority` sorts LOW < MEDIUM < HIGH and `status` sorts "
                    + "OPEN < IN_PROGRESS < RESOLVED < CLOSED < CANCELLED.",
                    schema = @Schema(allowableValues = {"id", "createdAt", "updatedAt", "priority", "status", "category", "resolutionDueAt"},
                            defaultValue = "createdAt"))
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (case-insensitive).",
                    schema = @Schema(allowableValues = {"asc", "desc"}, defaultValue = "desc"))
            @RequestParam(required = false) String direction,
            @Parameter(description = "Only tickets in one of these statuses. Repeat the parameter or separate values with commas.",
                    array = @ArraySchema(schema = @Schema(implementation = TicketStatus.class)))
            @RequestParam(required = false) List<String> status,
            @Parameter(description = "Only tickets with one of these priorities. Repeat the parameter or separate values with commas.",
                    array = @ArraySchema(schema = @Schema(implementation = TicketPriority.class)))
            @RequestParam(required = false) List<String> priority,
            @Parameter(description = "Only tickets created on or after this UTC date (inclusive), `yyyy-MM-dd`.",
                    schema = @Schema(type = "string", format = "date", example = "2026-11-01"))
            @RequestParam(required = false) String createdFrom,
            @Parameter(description = "Only tickets created on or before this UTC date (inclusive), `yyyy-MM-dd`. "
                    + "Must not be earlier than `createdFrom`.",
                    schema = @Schema(type = "string", format = "date", example = "2026-11-30"))
            @RequestParam(required = false) String createdTo,
            @Parameter(description = "Search text (max 100 characters): case-insensitive substring of the title or "
                    + "description, or a ticket number (`42` or `#42`). `%`, `_` and `\\` match literally.",
                    schema = @Schema(maxLength = 100, example = "printer"))
            @RequestParam(required = false) String q,
            @Parameter(description = "Only tickets in one of these categories (repeat or comma-separate).",
                    array = @ArraySchema(schema = @Schema(implementation = TicketCategory.class)))
            @RequestParam(required = false) List<String> category,
            @Parameter(description = "Pre-defined queue: `all` (default), `mine` (raised by me), `assigned` (assigned to me), "
                    + "`team` (my teams), `unassigned` (active, no agent).",
                    schema = @Schema(allowableValues = {"all", "mine", "assigned", "team", "unassigned"}))
            @RequestParam(required = false) String view,
            @Parameter(description = "Only tickets of this team.") @RequestParam(required = false) String teamId,
            @Parameter(description = "Only tickets assigned to this account.") @RequestParam(required = false) String assigneeId,
            @Parameter(description = "Only tickets raised by this account.") @RequestParam(required = false) String requesterId) {
        return ticketService.listTickets(TicketListQuery.parse(
                page, size, sortBy, direction, status, priority, createdFrom, createdTo, q,
                category, view, teamId, assigneeId, requesterId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ticket",
            description = "Returns one ticket by its id. Tickets the caller may not see are reported as 404, like missing ones.")
    @ApiResponse(responseCode = "200", description = "The ticket")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No ticket with this id",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public TicketResponse getTicketById(@Parameter(description = "Ticket id", example = "42") @PathVariable Long id) {
        return ticketService.getTicketById(id);
    }

    @PostMapping
    @Operation(summary = "Create a ticket",
            description = "Creates a ticket. New tickets always start as OPEN; `id`, timestamps and `version` are "
                    + "assigned by the server. `status`, if sent, must be OPEN.")
    @ApiResponse(responseCode = "201", description = "The created ticket",
            headers = @Header(name = "Location", description = "URL of the new ticket, e.g. /api/tickets/42",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "Validation failed or the body is not valid JSON; `fieldErrors` has one message per invalid field",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = """
                            {"timestamp":"2026-11-21T12:38:00.123Z","status":400,"error":"Bad Request",
                             "message":"Validation failed",
                             "fieldErrors":{"title":"Title is required","priority":"Priority must be one of: LOW, MEDIUM, HIGH"}}""")))
    @ApiResponse(responseCode = "409", description = "The requested status is not OPEN (new tickets always start as OPEN)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody TicketRequest request) {
        TicketResponse created = ticketService.createTicket(request);
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a ticket",
            description = "Replaces the title, description and priority, and optionally moves the ticket to another status "
                    + "(the allowed moves are listed in `allowedTransitions` of the ticket). Send the ticket's `version` "
                    + "to be told, instead of silently overwriting, when someone else changed it in the meantime. "
                    + "CLOSED and CANCELLED tickets can no longer be edited.")
    @ApiResponse(responseCode = "200", description = "The updated ticket")
    @ApiResponse(responseCode = "400", description = "Validation failed, the body is not valid JSON, or the id is not a number",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No ticket with this id",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requester changing details of a ticket that is no longer OPEN, or a status move the caller may not make",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Forbidden status change, ticket already CLOSED/CANCELLED, or stale `version`",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = """
                            {"timestamp":"2026-11-21T12:38:00.123Z","status":409,"error":"Conflict",
                             "message":"Cannot change ticket status from OPEN to RESOLVED. Allowed: [IN_PROGRESS, CANCELLED].",
                             "fieldErrors":{}}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public TicketResponse updateTicket(@Parameter(description = "Ticket id", example = "42") @PathVariable Long id,
            @Valid @RequestBody TicketRequest request) {
        return ticketService.updateTicket(id, request);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change a ticket's status",
            description = "Moves the ticket to one of its `allowedTransitions` (which already reflect the caller's "
                    + "permissions). Requesters may cancel an active ticket and reopen or close a RESOLVED one; staff with "
                    + "work access may make every move the workflow allows.")
    @ApiResponse(responseCode = "200", description = "The updated ticket")
    @ApiResponse(responseCode = "400", description = "Missing or unknown status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "The workflow allows the move but the caller may not make it",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No ticket with this id visible to the caller",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The workflow forbids the move, or stale `version`",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public TicketResponse changeStatus(@Parameter(description = "Ticket id", example = "42") @PathVariable Long id,
            @Valid @RequestBody StatusChangeRequest request) {
        return ticketService.changeStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    @Operation(summary = "Delete a ticket", description = "Administrators only. Permanently deletes a ticket in any status.")
    @ApiResponse(responseCode = "403", description = "Signed in without the ADMIN role")
    @ApiResponse(responseCode = "204", description = "Deleted (no body)")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No ticket with this id",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> deleteTicket(@Parameter(description = "Ticket id", example = "42") @PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
