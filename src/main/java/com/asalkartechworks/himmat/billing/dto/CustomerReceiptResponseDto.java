package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
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
public class CustomerReceiptResponseDto {
    private Long id;
    private String receiptNumber;
    private Long customerId;
    private String customerName;
    private LocalDate receiptDate;
    private BigDecimal totalAmount;
    private BigDecimal unallocatedAmount;
    private String notes;
    private List<PaymentComponentDto> components;
    private List<ReceiptAllocationDto> allocations;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
