package za.co.mamadise.support_ticket_tracker.comment;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import za.co.mamadise.support_ticket_tracker.auth.AnyRole;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@AnyRole
@Tag(name = "Comments", description = "Public comments and staff-only internal notes")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    @Operation(summary = "List comments",
            description = "Oldest first. Internal notes are included only for staff who work on the ticket.")
    @ApiResponse(responseCode = "404", description = "No such ticket visible to the caller")
    public List<CommentResponse> list(@PathVariable Long ticketId) {
        return commentService.list(ticketId);
    }

    @PostMapping
    @Operation(summary = "Add a comment or internal note")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Empty or too long")
    @ApiResponse(responseCode = "403", description = "Internal note by someone without work access")
    @ApiResponse(responseCode = "404", description = "No such ticket visible to the caller")
    @ApiResponse(responseCode = "409", description = "Ticket is CLOSED or CANCELLED")
    public ResponseEntity<CommentResponse> add(@PathVariable Long ticketId, @Valid @RequestBody CommentRequest request) {
        CommentResponse created = commentService.add(ticketId, request);
        return ResponseEntity.created(URI.create("/api/tickets/" + ticketId + "/comments/" + created.id())).body(created);
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Edit own comment")
    @ApiResponse(responseCode = "403", description = "Not the author, or the ticket is final")
    @ApiResponse(responseCode = "404", description = "No such comment visible to the caller")
    public CommentResponse edit(@PathVariable Long ticketId, @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        return commentService.edit(ticketId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment (administrators)")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "Not an administrator")
    public ResponseEntity<Void> delete(@PathVariable Long ticketId, @PathVariable Long commentId) {
        commentService.delete(ticketId, commentId);
        return ResponseEntity.noContent().build();
    }
}
