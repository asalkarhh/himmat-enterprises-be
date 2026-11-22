package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDto {
    private Long id;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerGstin;
    private String customerAddress;
    private Long siteId;
    private String siteName;
    private String siteAddress;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private InvoiceDisplayFormat displayFormat;
    private InvoiceStatus status;
    private SettlementStatus settlementStatus;
    private BigDecimal subtotalExclTax;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalGstAmount;
    private BigDecimal totalPayableAmount;
    private BigDecimal totalAllocatedAmount;
    private BigDecimal outstandingAmount;
    private boolean taxBreakdownAvailable;
    private String pricingFingerprint;
    private String notes;

    @com.fasterxml.jackson.annotation.JsonProperty("isOverdue")
    private boolean isOverdue;

    private long daysOverdue;
    private List<InvoiceLineDto> lines;
    private List<ReceiptAllocationDto> allocations;
    private Long businessEventId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
