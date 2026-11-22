package com.asalkartechworks.himmat.party.dto;

import com.asalkartechworks.himmat.party.model.CustomerType;
import jakarta.validation.constraints.Email;
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
public class CreateCustomerRequest {

    @NotNull(message = "Customer type is required")
    private CustomerType customerType;

    @NotBlank(message = "Customer name is required")
    @Size(min = 1, max = 200, message = "Customer name must not exceed 200 characters")
    private String name;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 20, message = "GSTIN must not exceed 20 characters")
    private String gstin;

    private String addressLine1;
    private String addressLine2;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    private String notes;
}
