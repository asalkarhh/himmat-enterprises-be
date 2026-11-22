package com.asalkartechworks.himmat.pricing.dto;

import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PricingPreviewLineResponse {

    private Long variantId;
    private String sku;
    private String productName;
    private String sizeLabel;
    private PricingMethod pricingMethod;
    private Integer quantity;
    private BigDecimal baseInclusivePrice;
    private BigDecimal discountPct;
    private String discountSource;
    private BigDecimal unitNetInclusive;
    private BigDecimal lineTotal;
    private BigDecimal gstRatePct;
    private BigDecimal taxableAmount;
    private BigDecimal taxAmount;
    private boolean taxBreakdownAvailable;
    private boolean costAvailable;
    private BigDecimal profitAmount;
    private Long variantVersion;
    private Long categoryVersion;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
