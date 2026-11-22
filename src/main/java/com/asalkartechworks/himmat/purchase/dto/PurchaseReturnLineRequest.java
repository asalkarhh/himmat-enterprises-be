package com.asalkartechworks.himmat.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseReturnLineRequest {

    @NotNull(message = "purchaseLineId is required")
    private Long purchaseLineId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "returned quantity must be at least 1")
    private Integer quantity;
}
