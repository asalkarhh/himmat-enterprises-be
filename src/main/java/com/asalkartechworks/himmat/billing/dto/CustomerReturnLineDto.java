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
public class CustomerReturnLineDto {

    private Long id;
    private Long invoiceLineId;
    private Long variantId;
    private String sku;
    private String productName;
    private String sizeLabel;
    private Integer returnedQuantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String valuationBasis;
    private BigDecimal gstRatePct;
    private BigDecimal taxableAmount;
    private BigDecimal taxAmount;
}
