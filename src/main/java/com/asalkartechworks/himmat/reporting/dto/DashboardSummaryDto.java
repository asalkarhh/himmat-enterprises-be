package com.asalkartechworks.himmat.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {

    private LocalDate startDate;
    private LocalDate endDate;

    // Period Sales
    private BigDecimal grossSales;
    private BigDecimal revisionsDelta;
    private BigDecimal returnsCredit;
    private BigDecimal netSales;
    private int invoiceCount;

    // Collections
    private BigDecimal customerReceipts;
    private BigDecimal customerRefunds;
    private BigDecimal netCollections;
    private int receiptCount;

    // Customer Dues & Receivables
    private BigDecimal totalCustomerOutstanding;
    private BigDecimal overdueCustomerReceivables;
    private int customersWithDuesCount;

    // Period Purchases & Payables
    private BigDecimal periodPurchases;
    private BigDecimal periodPurchaseReturns;
    private BigDecimal netPurchases;
    private BigDecimal periodSupplierPayments;
    private BigDecimal periodSupplierRefunds;
    private BigDecimal totalSupplierOutstanding;
    private int suppliersWithPayablesCount;

    // Period Operating Expenses
    private BigDecimal periodOperatingExpenses;
    private int expenseCount;

    // Inventory Health
    private int totalVariantsCount;
    private int zeroStockVariantsCount;
    private int lowStockVariantsCount;

    // Profit Metrics (Owner only, null if not owner)
    private OwnerProfitSummaryDto profitSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerProfitSummaryDto {
        private BigDecimal netRevenue;
        private BigDecimal cogs;
        private BigDecimal grossProfit;
        private BigDecimal grossMarginPct;
        private BigDecimal operatingExpenses;
        private BigDecimal netProfit;
        private BigDecimal coveragePct;
        private BigDecimal unknownCostSales;
    }
}
