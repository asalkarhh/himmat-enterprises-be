package com.asalkartechworks.himmat.catalog.dto;

import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductVariantRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotBlank(message = "SKU is required")
    @Size(min = 1, max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    @NotBlank(message = "Size label is required")
    @Size(min = 1, max = 120, message = "Size label must not exceed 120 characters")
    private String sizeLabel;

    @NotNull(message = "Pricing method is required")
    private PricingMethod pricingMethod;

    @DecimalMin(value = "0.00", message = "MRP inclusive of tax must be non-negative")
    private BigDecimal mrpInclTax;

    @DecimalMin(value = "0.00", message = "Fixed price inclusive of tax must be non-negative")
    private BigDecimal fixedPriceInclTax;

    @DecimalMin(value = "0.0000", message = "Purchase cost exclusive of tax must be non-negative")
    private BigDecimal purchaseCostExclTax;

    @DecimalMin(value = "0.0000", message = "Profit percentage must be non-negative")
    private BigDecimal profitPct;

    @DecimalMin(value = "0.0000", message = "GST rate percentage must be at least 0")
    @DecimalMax(value = "100.0000", message = "GST rate percentage must not exceed 100")
    private BigDecimal gstRatePct;

    @Size(max = 20, message = "HSN code must not exceed 20 characters")
    private String hsnCode;

    @Min(value = 0, message = "Low stock threshold must be non-negative")
    @Builder.Default
    private Integer lowStockThreshold = 0;
}
