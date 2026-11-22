package za.co.mamadise.support_ticket_tracker.attachment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import za.co.mamadise.support_ticket_tracker.auth.AnyRole;

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
@AnyRole
@Tag(name = "Attachments", description = "Files attached to tickets (PNG, JPEG, PDF, TXT, LOG; max 5 MB)")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    @Operation(summary = "List attachments")
    @ApiResponse(responseCode = "404", description = "No such ticket visible to the caller")
    public List<AttachmentResponse> list(@PathVariable Long ticketId) {
        return attachmentService.list(ticketId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an attachment", description = "multipart/form-data with the file in the part `file`.")
    @ApiResponse(responseCode = "201", description = "Stored")
    @ApiResponse(responseCode = "400", description = "Empty, disallowed type, content not matching the type, or too large")
    @ApiResponse(responseCode = "404", description = "No such ticket visible to the caller")
    @ApiResponse(responseCode = "409", description = "Ticket final, or attachment limit reached")
    @ApiResponse(responseCode = "413", description = "Request larger than the upload limit")
    public ResponseEntity<AttachmentResponse> upload(@PathVariable Long ticketId, @RequestPart("file") MultipartFile file) {
        AttachmentResponse created = attachmentService.upload(ticketId, file);
        return ResponseEntity.created(URI.create(created.downloadUrl())).body(created);
    }

    /**
     * Always served as a download ({@code Content-Disposition: attachment}) with the stored, server-detected type and
     * {@code nosniff}, so a file can never be rendered as a page of this site.
     */
    @GetMapping("/{attachmentId}/download")
    @Operation(summary = "Download an attachment")
    @ApiResponse(responseCode = "404", description = "No such attachment visible to the caller")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long ticketId, @PathVariable Long attachmentId) {
        AttachmentDownload download = attachmentService.download(ticketId, attachmentId);
        MediaType type = MediaType.parseMediaType(download.contentType());
        if (type.getType().equals("text")) {
            type = new MediaType(type, StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(summary = "Delete an attachment", description = "The uploader (ticket not final) or an administrator.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "Not allowed")
    public ResponseEntity<Void> delete(@PathVariable Long ticketId, @PathVariable Long attachmentId) {
        attachmentService.delete(ticketId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
