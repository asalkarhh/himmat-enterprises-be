package com.asalkartechworks.himmat.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseRequest {

    @NotNull(message = "supplierId is required")
    private Long supplierId;

    private String supplierInvoiceNumber;

    private LocalDate purchaseDate;

    private String notes;

    @NotEmpty(message = "Purchase must contain at least one line")
    @Valid
    private List<PurchaseLineRequest> lines;

    @Valid
    private List<PaymentComponentDto> paymentComponents;
}
