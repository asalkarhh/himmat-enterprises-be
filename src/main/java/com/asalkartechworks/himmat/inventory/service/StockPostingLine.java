package com.asalkartechworks.himmat.inventory.service;

import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPostingLine {

    private Long variantId;
    private StockMovementType movementType;
    private StockMovementDirection direction;
    private Integer quantity;
    private Long expectedVersion;
    private Long sourceLineId;
    private String sourceEffectKey;
    private String lineNote;
}
