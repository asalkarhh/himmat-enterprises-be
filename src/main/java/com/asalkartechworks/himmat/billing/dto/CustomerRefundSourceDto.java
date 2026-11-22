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
public class CustomerRefundSourceDto {

    private Long id;
    private Long customerReceiptId;
    private String receiptNumber;
    private BigDecimal amount;
    private Instant createdAt;
}
