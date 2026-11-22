package com.asalkartechworks.himmat.common.dto;

import com.asalkartechworks.himmat.common.filter.CorrelationIdHolder;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standard API response wrapper for paginated list payloads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard paginated API response wrapper")
public class PageResponse<T> {

    @Schema(description = "List of items in current page")
    private List<T> data;

    @Schema(description = "Zero-indexed page number", example = "0")
    private int page;

    @Schema(description = "Page size", example = "20")
    private int size;

    @Schema(description = "Total number of elements across all pages", example = "100")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "5")
    private int totalPages;

    @Schema(description = "Request correlation ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @Builder.Default
    private String requestId = CorrelationIdHolder.get();

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .data(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .requestId(CorrelationIdHolder.get())
                .build();
    }
}
