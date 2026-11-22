package com.asalkartechworks.himmat.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class InvoiceLineRequest {

    @NotNull(message = "variantId is required")
    private Long variantId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @DecimalMin(value = "0.00", message = "unitPriceOverride must be non-negative")
    private BigDecimal unitPriceOverride;

    @DecimalMin(value = "0.0000", message = "discountPct cannot be negative")
    @DecimalMax(value = "100.0000", message = "discountPct cannot exceed 100")
    private BigDecimal discountPct;

    private String overrideReason;

    private Long expectedVariantVersion;
}
