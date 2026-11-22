package com.asalkartechworks.himmat.inventory.dto;

import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDto {

    private Long id;
    private Long variantId;
    private String sku;
    private String sizeLabel;
    private Long productId;
    private String productName;
    private Long businessEventId;
    private StockMovementType movementType;
    private StockMovementDirection direction;
    private Integer quantity;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private String sourceEntityType;
    private Long sourceEntityId;
    private Long sourceLineId;
    private String sourceEffectKey;
    private String reason;
    private Long actorId;
    private Instant occurredAt;
    private Instant createdAt;
}
