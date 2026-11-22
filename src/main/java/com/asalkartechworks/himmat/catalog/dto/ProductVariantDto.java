package com.asalkartechworks.himmat.catalog.dto;

import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private Long id;
    private Long productId;
    private String productName;
    private Long brandId;
    private String brandName;
    private Long categoryId;
    private String categoryName;
    private Long subcategoryId;
    private String subcategoryName;
    private String sku;
    private String sizeLabel;
    private PricingMethod pricingMethod;
    private BigDecimal mrpInclTax;
    private BigDecimal fixedPriceInclTax;
    private BigDecimal purchaseCostExclTax;
    private BigDecimal profitPct;
    private BigDecimal gstRatePct;
    private String hsnCode;
    private Integer lowStockThreshold;
    private Integer quantityOnHand;
    private boolean openingConfirmed;
    private boolean isActive;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
