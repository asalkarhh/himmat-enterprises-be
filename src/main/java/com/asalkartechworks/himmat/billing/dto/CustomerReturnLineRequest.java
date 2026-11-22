package com.asalkartechworks.himmat.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerReturnLineRequest {

    @NotNull(message = "invoiceLineId is required")
    private Long invoiceLineId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "Return quantity must be at least 1")
    private Integer quantity;

    /**
     * Optional valuation basis override for this line ("ORIGINAL_INVOICE_PRICE" or "LATEST_REVISED_PRICE").
     */
    private String valuationBasis;
}
