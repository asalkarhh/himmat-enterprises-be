package com.asalkartechworks.himmat.billing.dto;

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
public class ApplyCustomerAllocationRequest {

    @NotEmpty(message = "allocations must not be empty")
    @Valid
    private List<CustomerAllocationItemRequest> allocations;
}
