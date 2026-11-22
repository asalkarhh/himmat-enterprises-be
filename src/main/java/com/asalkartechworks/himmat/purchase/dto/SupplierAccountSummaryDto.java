package com.asalkartechworks.himmat.purchase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierAccountSummaryDto {
    private Long supplierId;
    private String supplierCode;
    private String supplierName;

    /**
     * Positive = Shop owes supplier (Payable)
     * Negative = Supplier owes shop or holds advance (Credit)
     */
    private BigDecimal currentBalance;

    private BigDecimal totalPurchases;
    private BigDecimal totalPaid;
    private BigDecimal totalReturned;
    private BigDecimal totalRefunded;
    private BigDecimal unallocatedCredit;
    private BigDecimal outstandingPayable;
}
