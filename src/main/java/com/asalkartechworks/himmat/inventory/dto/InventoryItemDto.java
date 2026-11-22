package com.asalkartechworks.himmat.inventory.dto;

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
public class InventoryItemDto {

    private Long variantId;
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
    private Integer quantityOnHand;
    private Integer lowStockThreshold;
    private boolean isLowStock;
    private boolean openingConfirmed;
    private Long version;
    private boolean isActive;
    private Instant updatedAt;
}
