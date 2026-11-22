package com.asalkartechworks.himmat.inventory.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPostingRequest {

    private Long businessEventId;
    private Long actorId;
    private Instant occurredAt;
    private String reason;
    private String sourceEntityType;
    private Long sourceEntityId;
    private boolean allowInactiveVariants;
    private List<StockPostingLine> lines;
}
