package com.asalkartechworks.himmat.inventory.dto;

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
public class ManualAdjustmentResponse {

    private Long eventId;
    private String eventKey;
    private String reason;
    private Instant occurredAt;
    private List<ManualAdjustmentLineResultDto> lines;
}
