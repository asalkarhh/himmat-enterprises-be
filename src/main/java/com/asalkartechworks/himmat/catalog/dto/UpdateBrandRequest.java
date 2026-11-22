package com.asalkartechworks.himmat.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBrandRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @Size(min = 1, max = 120, message = "Brand name must not exceed 120 characters")
    private String name;

    private Boolean isActive;
}
