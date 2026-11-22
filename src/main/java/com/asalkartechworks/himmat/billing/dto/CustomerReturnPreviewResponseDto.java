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
public class CustomerReturnPreviewResponseDto {

    private Long invoiceId;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;
    private BigDecimal totalReturnAmount;
    private BigDecimal previousAllocatedAmount;
    private BigDecimal releasedAllocationAmount;
    private BigDecimal newEffectivePayableAmount;
    private BigDecimal newAllocatedAmount;
    private BigDecimal newOutstandingAmount;
    private List<CustomerReturnLineDto> lines;
}
