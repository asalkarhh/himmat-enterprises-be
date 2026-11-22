package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePreviewRequest {

    @NotNull(message = "customerId is required")
    private Long customerId;

    private Long siteId;

    @Builder.Default
    private InvoiceDisplayFormat displayFormat = InvoiceDisplayFormat.NORMAL;

    @NotEmpty(message = "Preview must contain at least one line")
    @Valid
    private List<InvoiceLineRequest> lines;
}
