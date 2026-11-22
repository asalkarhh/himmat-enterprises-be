package com.asalkartechworks.himmat.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class CreatePurchaseReturnRequest {

    private LocalDate returnDate;

    @NotBlank(message = "Return reason is required")
    private String reason;

    @NotEmpty(message = "At least one return line is required")
    @Valid
    private List<PurchaseReturnLineRequest> lines;
}
