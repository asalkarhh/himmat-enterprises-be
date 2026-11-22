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
public class DuesSummaryDto {
    private BigDecimal totalOutstandingAmount;
    private BigDecimal totalOverdueAmount;
    private long outstandingInvoiceCount;
    private long overdueInvoiceCount;
}
