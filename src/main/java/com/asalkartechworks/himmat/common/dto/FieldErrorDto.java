package com.asalkartechworks.himmat.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Validation failure details for a specific field")
public class FieldErrorDto {

    @Schema(description = "Name of the invalid field", example = "username")
    private String field;

    @Schema(description = "Validation message", example = "Username must not be blank")
    private String message;
}
