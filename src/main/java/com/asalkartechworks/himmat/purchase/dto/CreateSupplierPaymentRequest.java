package com.asalkartechworks.himmat.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSupplierPaymentRequest {

    private LocalDate paymentDate;

    @NotNull(message = "totalAmount is required")
    @DecimalMin(value = "0.01", message = "totalAmount must be greater than 0")
    private BigDecimal totalAmount;

    private String notes;

    @NotEmpty(message = "At least one payment component is required")
    @Valid
    private List<PaymentComponentDto> components;

    @Valid
    private List<PaymentAllocationRequest> allocations;
}
