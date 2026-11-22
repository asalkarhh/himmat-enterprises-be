package com.asalkartechworks.himmat.billing.dto;

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
public class ReceiptAllocationDto {
    private Long id;
    private Long receiptId;
    private String receiptNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private BigDecimal allocatedAmount;
    private Instant createdAt;
}
