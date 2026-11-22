package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePreviewLineDto {
    private Long variantId;
    private String sku;
    private String productName;
    private String sizeLabel;
    private PricingMethod pricingMethod;
    private Integer quantity;
    private BigDecimal baseUnitPrice;
    private BigDecimal discountPct;
    private String discountSource;
    private BigDecimal unitNetPrice;
    private BigDecimal lineTotal;
    private BigDecimal gstRatePct;
    private BigDecimal taxableAmount;
    private BigDecimal taxAmount;
    private boolean taxBreakdownAvailable;
    private BigDecimal purchaseCostExclTax;
    private boolean costAvailable;
    private boolean isPriceOverridden;
    private String overrideReason;
    private Long variantVersion;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
