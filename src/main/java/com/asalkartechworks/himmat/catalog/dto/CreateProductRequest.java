package com.asalkartechworks.himmat.catalog.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateProductRequest {

    @NotNull(message = "Brand ID is required")
    private Long brandId;

    @NotNull(message = "Subcategory ID is required")
    private Long subcategoryId;

    @NotBlank(message = "Product name is required")
    @Size(min = 1, max = 240, message = "Product name must not exceed 240 characters")
    private String name;

    private String description;
}
