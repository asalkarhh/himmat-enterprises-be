package com.asalkartechworks.himmat.purchase.dto;

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
public class ApplySupplierAllocationRequest {

    @NotEmpty(message = "At least one allocation item is required")
    @Valid
    private List<AllocationItemRequest> allocations;
}
