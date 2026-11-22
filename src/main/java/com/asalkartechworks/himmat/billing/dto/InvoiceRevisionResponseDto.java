package com.asalkartechworks.himmat.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRevisionResponseDto {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private Integer revisionNumber;
    private String reason;
    private Long businessEventId;
    private Long actorId;
    private BigDecimal previousSubtotalExclTax;
    private BigDecimal previousTotalDiscount;
    private BigDecimal previousTotalTaxable;
    private BigDecimal previousTotalGst;
    private BigDecimal previousTotalPayable;
    private BigDecimal newSubtotalExclTax;
    private BigDecimal newTotalDiscount;
    private BigDecimal newTotalTaxable;
    private BigDecimal newTotalGst;
    private BigDecimal newTotalPayable;
    private BigDecimal deltaPayableAmount;
    private BigDecimal releasedAllocationAmount;
    private String snapshotPayload;
    private List<InvoiceRevisionLineDto> lines;
    private Instant createdAt;
}
