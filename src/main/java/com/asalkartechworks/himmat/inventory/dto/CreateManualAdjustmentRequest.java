package com.asalkartechworks.himmat.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateManualAdjustmentRequest {

    @NotBlank(message = "Reason is required")
    @Size(min = 3, max = 500, message = "Reason must be between 3 and 500 characters")
    private String reason;

    @NotEmpty(message = "Adjustment must contain at least one line")
    @Size(max = 100, message = "Adjustment cannot contain more than 100 lines")
    @Valid
    private List<ManualAdjustmentLineRequest> lines;
}
