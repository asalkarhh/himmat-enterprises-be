package com.asalkartechworks.himmat.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRevisionLineRequest {

    /**
     * Optional ID of the existing SalesInvoiceLine being revised.
     * If null, indicates a newly added line to this invoice.
     */
    private Long invoiceLineId;

    @NotNull(message = "variantId is required")
    private Long variantId;

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity must be non-negative (0 to remove line)")
    private Integer quantity;

    @DecimalMin(value = "0.0", message = "unitPriceOverride must be non-negative")
    @Digits(integer = 14, fraction = 4, message = "unitPriceOverride format invalid")
    private BigDecimal unitPriceOverride;

    @DecimalMin(value = "0.0", message = "discountPct must be non-negative")
    @Digits(integer = 3, fraction = 4, message = "discountPct format invalid")
    private BigDecimal discountPct;

    private String overrideReason;

    private Long expectedVariantVersion;
}
