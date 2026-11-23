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
public class PurchaseReportDto {

    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal totalGrossPurchases;
    private BigDecimal totalPurchaseReturns;
    private BigDecimal totalNetPurchases;
    private BigDecimal totalSupplierPayments;
    private BigDecimal totalSupplierRefunds;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxAmount;

    private List<PurchaseRowItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseRowItemDto {
        private String documentNumber;
        private LocalDate documentDate;
        private Long supplierId;
        private String supplierName;
        private String type; // PURCHASE, PURCHASE_RETURN
        private String supplierInvoiceNumber;
        private BigDecimal grossOrDeltaAmount;
        private BigDecimal taxableAmount;
        private BigDecimal taxAmount;
    }
}
