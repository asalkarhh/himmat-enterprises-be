package com.asalkartechworks.himmat.purchase.dto;

import com.asalkartechworks.himmat.purchase.model.PriceBasis;
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
public class PurchaseLineRequest {

    @NotNull(message = "variantId is required")
    private Long variantId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "priceBasis is required")
    private PriceBasis priceBasis;

    @NotNull(message = "enteredUnitPrice is required")
    @DecimalMin(value = "0.01", message = "enteredUnitPrice must be greater than 0")
    private BigDecimal enteredUnitPrice;

    @DecimalMin(value = "0.0", message = "discountPct cannot be negative")
    @DecimalMax(value = "100.0", message = "discountPct cannot exceed 100")
    private BigDecimal discountPct;

    @DecimalMin(value = "0.0", message = "gstRatePct cannot be negative")
    @DecimalMax(value = "100.0", message = "gstRatePct cannot exceed 100")
    private BigDecimal gstRatePct;

    @Builder.Default
    private Boolean updateProductCost = false;

    private Long variantVersion;
}
