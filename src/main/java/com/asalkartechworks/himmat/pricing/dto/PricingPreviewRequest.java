package com.asalkartechworks.himmat.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingPreviewRequest {

    @NotEmpty(message = "At least one preview line is required")
    private List<@Valid PricingPreviewLineRequest> lines;
}
