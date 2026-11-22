package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
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
public class InvoicePreviewResponseDto {
    private Long customerId;
    private String customerName;
    private Long siteId;
    private String siteName;
    private InvoiceDisplayFormat displayFormat;
    private List<InvoicePreviewLineDto> lines;
    private BigDecimal grossTotal;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal totalPayableAmount;
    private boolean taxBreakdownAvailable;
    private String pricingFingerprint;
    private List<String> warnings;
    private boolean gstFormatCompletenessWarning;
}
