package com.asalkartechworks.himmat.purchase.dto;

import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierLedgerEntryDto {
    private Long id;
    private Long businessEventId;
    private SupplierLedgerSourceType sourceType;
    private Long sourceId;

    /**
     * Signed amount:
     * Positive = Shop owes supplier (Payable)
     * Negative = Supplier owes shop or holds advance (Credit)
     */
    private BigDecimal amount;
    private BigDecimal runningBalance;
    private LocalDate effectiveDate;
    private String notes;
    private Instant createdAt;
}
