package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
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
public class CustomerLedgerEntryDto {
    private Long id;
    private Long businessEventId;
    private CustomerLedgerSourceType sourceType;
    private Long sourceId;

    /**
     * Signed amount:
     * Positive = Customer owes shop (Receivable)
     * Negative = Shop owes customer or holds advance (Credit)
     */
    private BigDecimal amount;
    private BigDecimal runningBalance;
    private LocalDate effectiveDate;
    private String notes;
    private Instant createdAt;
}
