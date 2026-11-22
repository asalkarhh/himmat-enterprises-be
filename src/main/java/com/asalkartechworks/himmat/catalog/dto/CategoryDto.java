package com.asalkartechworks.himmat.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    private String name;
    private BigDecimal purchaseDiscountPct;
    private BigDecimal salesDiscountPct;
    private boolean isActive;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
