package com.asalkartechworks.himmat.common.dto;

import com.asalkartechworks.himmat.common.filter.CorrelationIdHolder;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response wrapper for singular data payloads and mutations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard single-resource API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Data payload")
    private T data;

    @Schema(description = "Request correlation ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @Builder.Default
    private String requestId = CorrelationIdHolder.get();

    public static <T> ApiResponse<T> of(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .requestId(CorrelationIdHolder.get())
                .build();
    }
}
