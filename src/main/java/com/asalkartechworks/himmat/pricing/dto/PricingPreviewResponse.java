package com.asalkartechworks.himmat.pricing.dto;

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
public class PricingPreviewResponse {

    @Builder.Default
    private List<PricingPreviewLineResponse> lines = new ArrayList<>();

    private BigDecimal grossTotal;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxAmount;
    private boolean taxBreakdownAvailable;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
