package com.asalkartechworks.himmat.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitReportDto {

    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal knownNetRevenue;
    private BigDecimal knownCogs;
    private BigDecimal knownGrossProfit;
    private BigDecimal knownGrossMarginPct;
    private BigDecimal salesValueExcludedUnknownCost;
    private BigDecimal totalNetSales;
    private BigDecimal costCoveragePct;
    private BigDecimal totalOperatingExpenses;
    private BigDecimal estimatedNetProfit;

    private List<ProductProfitItemDto> productBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductProfitItemDto {
        private Long variantId;
        private Long productId;
        private String sku;
        private String productName;
        private String sizeLabel;
        private String brandName;
        private String categoryName;
        private int quantitySold;
        private int quantityReturned;
        private int netQuantity;
        private BigDecimal netRevenue;
        private BigDecimal netCogs;
        private BigDecimal grossProfit;
        private BigDecimal grossMarginPct;
        private boolean costAvailable;
    }
}
