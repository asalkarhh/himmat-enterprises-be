package com.asalkartechworks.himmat.purchase.dto;

import com.asalkartechworks.himmat.common.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierLedgerStatementDto {
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private PageResponse<SupplierLedgerEntryDto> entries;
}
