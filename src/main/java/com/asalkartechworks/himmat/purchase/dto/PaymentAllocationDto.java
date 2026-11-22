package com.asalkartechworks.himmat.purchase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAllocationDto {
    private Long id;
    private Long purchaseId;
    private String purchaseNumber;
    private BigDecimal allocatedAmount;
    private Instant createdAt;
}
