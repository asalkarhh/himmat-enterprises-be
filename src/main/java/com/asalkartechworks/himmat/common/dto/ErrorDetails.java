package com.asalkartechworks.himmat.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error details")
public class ErrorDetails {

    @Schema(description = "Machine-readable error code", example = "STALE_VERSION")
    private String code;

    @Schema(description = "Human-readable error description", example = "The record changed. Reload before editing.")
    private String message;

    @Schema(description = "List of specific field validation errors, if applicable")
    @Builder.Default
    private List<FieldErrorDto> fields = new ArrayList<>();

    @Schema(description = "Current database version of the record when a version conflict occurs", example = "2")
    private Long currentVersion;
}
