package com.asalkartechworks.himmat.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class CreateSupplierRefundRequest {

    private LocalDate refundDate;

    @NotNull(message = "totalAmount is required")
    @DecimalMin(value = "0.01", message = "totalAmount must be greater than 0")
    private BigDecimal totalAmount;

    @NotBlank(message = "Refund reason is required")
    private String reason;

    @NotEmpty(message = "At least one refund component is required")
    @Valid
    private List<PaymentComponentDto> components;
}
