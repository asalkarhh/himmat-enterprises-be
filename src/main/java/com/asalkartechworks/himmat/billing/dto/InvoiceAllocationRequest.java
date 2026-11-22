package com.asalkartechworks.himmat.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceAllocationRequest {

    @NotNull(message = "invoiceId is required")
    private Long invoiceId;

    @NotNull(message = "allocated amount is required")
    @DecimalMin(value = "0.01", message = "allocated amount must be greater than 0")
    private BigDecimal amount;
}
