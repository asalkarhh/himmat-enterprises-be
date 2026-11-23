package com.asalkartechworks.himmat.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReportDto {

    private int totalVariants;
    private int totalStockQuantity;
    private BigDecimal totalStockValuation;
    private int zeroStockCount;
    private int lowStockCount;

    private List<InventoryReportRowDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryReportRowDto {
        private Long variantId;
        private Long productId;
        private String sku;
        private String productName;
        private String sizeLabel;
        private String brandName;
        private String categoryName;
        private String subcategoryName;
        private int currentQuantity;
        private Integer reorderLevel;
        private String stockStatus; // IN_STOCK, LOW_STOCK, OUT_OF_STOCK
        private BigDecimal purchaseCostExclTax;
        private boolean costAvailable;
        private BigDecimal valuationAmount;
    }
}
