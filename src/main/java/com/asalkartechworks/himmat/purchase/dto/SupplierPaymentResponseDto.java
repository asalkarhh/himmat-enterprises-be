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
public class SupplierPaymentResponseDto {
    private Long id;
    private String paymentNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate paymentDate;
    private BigDecimal totalAmount;
    private BigDecimal unallocatedAmount;
    private String notes;
    private List<PaymentComponentDto> components;
    private List<PaymentAllocationDto> allocations;
    private Long version;
    private Instant createdAt;
}
