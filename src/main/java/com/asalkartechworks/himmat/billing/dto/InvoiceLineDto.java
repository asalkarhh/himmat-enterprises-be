package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLineDto {
    private Long id;
    private Long variantId;
    private Integer lineIndex;
    private String productName;
    private String sku;
    private String sizeLabel;
    private PricingMethod pricingMethod;
    private Integer quantity;
    private BigDecimal baseUnitPrice;
    private BigDecimal discountPct;
    private String discountSource;
    private BigDecimal unitNetPrice;
    private BigDecimal lineTotal;
    private boolean isPriceOverridden;
    private String overrideReason;
    private BigDecimal gstRatePct;
    private BigDecimal taxableAmount;
    private BigDecimal taxAmount;
    private boolean taxBreakdownAvailable;
    private BigDecimal purchaseCostExclTax;
    private boolean costAvailable;
}
