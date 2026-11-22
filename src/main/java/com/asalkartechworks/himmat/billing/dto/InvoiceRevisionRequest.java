package com.asalkartechworks.himmat.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRevisionRequest {

    private Long expectedVersion;

    @NotBlank(message = "Revision reason is required")
    private String reason;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<InvoiceRevisionLineRequest> lines;
}
