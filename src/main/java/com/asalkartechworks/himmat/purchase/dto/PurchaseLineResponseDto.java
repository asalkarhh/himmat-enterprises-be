package com.asalkartechworks.himmat.purchase.dto;

import com.asalkartechworks.himmat.purchase.model.PriceBasis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLineResponseDto {
    private Long id;
    private Long variantId;
    private Integer lineIndex;
    private String productName;
    private String sku;
    private String sizeLabel;
    private Integer quantity;
    private Integer returnedQuantity;
    private Integer remainingQuantity;
    private PriceBasis priceBasis;
    private BigDecimal enteredUnitPrice;
    private BigDecimal discountPct;
    private BigDecimal gstRatePct;
    private BigDecimal netUnitCost;
    private BigDecimal grossUnitCost;
    private BigDecimal lineTotal;
    private boolean updateProductCost;
}
