package com.asalkartechworks.himmat.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private Long brandId;
    private String brandName;
    private Long subcategoryId;
    private String subcategoryName;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private boolean isActive;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
