package com.asalkartechworks.himmat.inventory.service;

import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostedLineResult {

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
