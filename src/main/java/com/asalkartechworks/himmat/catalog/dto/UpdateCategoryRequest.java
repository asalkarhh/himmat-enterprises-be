package com.asalkartechworks.himmat.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @Size(min = 1, max = 120, message = "Category name must not exceed 120 characters")
    private String name;

    @DecimalMin(value = "0.0000", message = "Purchase discount percentage must be at least 0")
    @DecimalMax(value = "100.0000", message = "Purchase discount percentage must not exceed 100")
    private BigDecimal purchaseDiscountPct;

    @DecimalMin(value = "0.0000", message = "Sales discount percentage must be at least 0")
    @DecimalMax(value = "100.0000", message = "Sales discount percentage must not exceed 100")
    private BigDecimal salesDiscountPct;

    private Boolean isActive;
}
