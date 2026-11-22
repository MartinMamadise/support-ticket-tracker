package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/**
 * One page of results plus paging metadata, so the API does not expose Spring's {@code Page} type.
 *
 * @param page          zero-based number of this page
 * @param size          page size used (after applying the maximum)
 * @param totalElements number of matching items across all pages
 * @param totalPages    number of pages ({@code 0} when nothing matches)
 */
@Schema(description = "One page of results with paging metadata")
public record PageResponse<T>(
        @Schema(description = "The items on this page") List<T> content,
        @Schema(description = "Zero-based number of this page", example = "0") int page,
        @Schema(description = "Page size used (after applying the maximum of 100)", example = "20") int size,
        @Schema(description = "Number of matching items across all pages", example = "57") long totalElements,
        @Schema(description = "Number of pages; 0 when nothing matches", example = "3") int totalPages,
        @Schema(description = "Whether a later page exists") boolean hasNext,
        @Schema(description = "Whether an earlier page exists") boolean hasPrevious) {

    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext(),
                source.hasPrevious());
    }
}
