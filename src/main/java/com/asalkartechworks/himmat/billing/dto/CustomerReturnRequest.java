package com.asalkartechworks.himmat.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerReturnRequest {

    private Long expectedInvoiceVersion;

    private LocalDate returnDate;

    @NotBlank(message = "Return reason is required")
    private String reason;

    /**
     * Valuation policy for lines whose prices were changed in revisions:
     * "ORIGINAL_INVOICE_PRICE" or "LATEST_REVISED_PRICE".
     */
    private String valuationPolicy;

    @NotEmpty(message = "At least one return line is required")
    @Valid
    private List<CustomerReturnLineRequest> lines;
}
