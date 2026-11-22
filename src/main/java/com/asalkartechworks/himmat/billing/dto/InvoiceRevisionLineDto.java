package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.billing.model.RevisionChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRevisionLineDto {

    private Long id;
    private Long invoiceLineId;
    private Long variantId;
    private Integer lineIndex;
    private RevisionChangeType changeType;
    private String sku;
    private String productName;
    private String sizeLabel;
    private Integer previousQuantity;
    private Integer newQuantity;
    private Integer deltaQuantity;
    private BigDecimal previousUnitNetPrice;
    private BigDecimal newUnitNetPrice;
    private BigDecimal previousLineTotal;
    private BigDecimal newLineTotal;
    private BigDecimal deltaLineTotal;
    private boolean isPriceOverridden;
    private String overrideReason;
}
