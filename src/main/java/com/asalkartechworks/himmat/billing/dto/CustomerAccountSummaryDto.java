package com.asalkartechworks.himmat.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAccountSummaryDto {
    private Long customerId;
    private String customerCode;
    private String customerName;

    /**
     * Positive = Customer owes shop (Receivable)
     * Negative = Shop owes customer or holds advance (Credit)
     */
    private BigDecimal currentBalance;

    private BigDecimal totalInvoiced;
    private BigDecimal totalReceipts;
    private BigDecimal totalReturned;
    private BigDecimal totalRefunded;
    private BigDecimal unallocatedCredit;
    private BigDecimal outstandingDues;
}
