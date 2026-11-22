package com.asalkartechworks.himmat.billing.dto;

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
public class DueInvoiceDto {
    private Long invoiceId;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;
    private Long siteId;
    private String siteName;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal totalPayableAmount;
    private BigDecimal totalAllocatedAmount;
    private BigDecimal outstandingAmount;

    @com.fasterxml.jackson.annotation.JsonProperty("isOverdue")
    private boolean isOverdue;

    private long daysOverdue;
}
