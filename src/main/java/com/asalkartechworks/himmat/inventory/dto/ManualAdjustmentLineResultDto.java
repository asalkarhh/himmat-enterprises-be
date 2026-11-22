package com.asalkartechworks.himmat.inventory.dto;

import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAdjustmentLineResultDto {

    private Long variantId;
    private String sku;
    private String productName;
    private StockMovementDirection direction;
    private Integer quantity;
    private Integer previousQuantityOnHand;
    private Integer newQuantityOnHand;
    private Long previousVersion;
    private Long newVersion;
    private Long movementId;
}
