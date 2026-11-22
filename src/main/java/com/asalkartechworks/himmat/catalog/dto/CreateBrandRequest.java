package com.asalkartechworks.himmat.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBrandRequest {

    @NotBlank(message = "Brand name is required")
    @Size(min = 1, max = 120, message = "Brand name must not exceed 120 characters")
    private String name;
}
