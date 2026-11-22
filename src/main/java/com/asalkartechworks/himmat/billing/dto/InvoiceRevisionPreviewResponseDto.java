package com.asalkartechworks.himmat.billing.dto;

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
public class InvoiceRevisionPreviewResponseDto {

    private Long invoiceId;
    private String invoiceNumber;
    private Integer revisionNumber;
    private BigDecimal previousTotalPayable;
    private BigDecimal newTotalPayable;
    private BigDecimal deltaPayableAmount;
    private BigDecimal previousAllocatedAmount;
    private BigDecimal releasedAllocationAmount;
    private BigDecimal newAllocatedAmount;
    private BigDecimal newOutstandingAmount;
    private List<InvoiceRevisionLineDto> lines;
}
