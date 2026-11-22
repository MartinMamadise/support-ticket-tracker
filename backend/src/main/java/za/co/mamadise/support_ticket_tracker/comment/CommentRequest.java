package za.co.mamadise.support_ticket_tracker.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A comment or internal note")
public record CommentRequest(
        @NotBlank(message = "Comment text is required")
        @Size(max = 5000, message = "Comment must be at most 5000 characters")
        @Schema(example = "I restarted the printer; please try again.") String body,
        @Schema(description = "True for an internal note (staff only). Ignored on edit.", nullable = true)
        Boolean internal) {

    boolean isInternal() {
        return Boolean.TRUE.equals(internal);
    }
}
