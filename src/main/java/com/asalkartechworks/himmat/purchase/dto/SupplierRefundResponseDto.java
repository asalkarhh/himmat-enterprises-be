package com.asalkartechworks.himmat.purchase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRefundResponseDto {
    private Long id;
    private String refundNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate refundDate;
    private BigDecimal totalAmount;
    private String reason;
    private List<PaymentComponentDto> components;
    private Long version;
    private Instant createdAt;
}
