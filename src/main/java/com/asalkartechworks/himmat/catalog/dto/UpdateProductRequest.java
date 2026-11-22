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
public class UpdateProductRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    private Long brandId;

    private Long subcategoryId;

    @Size(min = 1, max = 240, message = "Product name must not exceed 240 characters")
    private String name;

    private String description;

    private Boolean isActive;
}
