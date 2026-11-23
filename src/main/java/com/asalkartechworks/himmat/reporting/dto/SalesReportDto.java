package com.asalkartechworks.himmat.reporting.dto;

import com.asalkartechworks.himmat.reporting.model.ReportingBasis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportDto {

    private LocalDate startDate;
    private LocalDate endDate;
    private ReportingBasis basis;

    private BigDecimal totalGrossSales;
    private BigDecimal totalRevisionsDelta;
    private BigDecimal totalReturnsCredit;
    private BigDecimal totalNetSales;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal unknownTaxSales;

    private List<SalesRowItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesRowItemDto {
        private String documentNumber;
        private LocalDate documentDate;
        private Long customerId;
        private String customerName;
        private String type; // INVOICE, REVISION, RETURN
        private BigDecimal grossOrDeltaAmount;
        private BigDecimal returnsAmount;
        private BigDecimal netAmount;
        private BigDecimal taxableAmount;
        private BigDecimal taxAmount;
    }
}
