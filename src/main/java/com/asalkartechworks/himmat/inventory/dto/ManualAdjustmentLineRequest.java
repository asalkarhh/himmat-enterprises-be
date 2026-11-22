package com.asalkartechworks.himmat.inventory.dto;

import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
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
public class ManualAdjustmentLineRequest {

    @NotNull(message = "variantId is required")
    private Long variantId;

    @NotNull(message = "direction is required (IN or OUT)")
    private StockMovementDirection direction;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;
}
