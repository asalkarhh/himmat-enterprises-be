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
public class PurchaseReturnLineResponseDto {
    private Long id;
    private Long purchaseLineId;
    private Long variantId;
    private String productName;
    private String sku;
    private String sizeLabel;
    private Integer returnedQuantity;
    private BigDecimal unitCost;
    private BigDecimal totalAmount;
}
