package com.asalkartechworks.himmat.pricing.dto;

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
public class PricingPreviewLineRequest {

    @NotNull(message = "Variant ID is required")
    private Long variantId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be a positive integer piece")
    private Integer quantity;

    @DecimalMin(value = "0.00", message = "Unit price override must be non-negative")
    private BigDecimal unitPriceOverride;

    @DecimalMin(value = "0.0000", message = "Discount percentage must be at least 0")
    @DecimalMax(value = "100.0000", message = "Discount percentage must not exceed 100")
    private BigDecimal discountPct;

    private String overrideReason;
}
