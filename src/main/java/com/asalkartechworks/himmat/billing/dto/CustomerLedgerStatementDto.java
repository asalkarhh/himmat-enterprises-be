package com.asalkartechworks.himmat.billing.dto;

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
public class CustomerLedgerStatementDto {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private PageResponse<CustomerLedgerEntryDto> entries;
}
