package com.asalkartechworks.himmat.common.dto;

import com.asalkartechworks.himmat.common.filter.CorrelationIdHolder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standardized error response envelope")
public class ErrorResponse {

    @Schema(description = "Detailed error payload")
    private ErrorDetails error;

    @Schema(description = "Request correlation ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @Builder.Default
    private String requestId = CorrelationIdHolder.get();

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code(code)
                        .message(message)
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();
    }

    public static ErrorResponse of(String code, String message, Long currentVersion) {
        return ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code(code)
                        .message(message)
                        .currentVersion(currentVersion)
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();
    }

    public static ErrorResponse of(String code, String message, List<FieldErrorDto> fields) {
        return ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code(code)
                        .message(message)
                        .fields(fields)
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();
    }
}
