package com.asalkartechworks.himmat.party.dto;

import com.asalkartechworks.himmat.party.model.CustomerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {
    private Long id;
    private String customerCode;
    private CustomerType customerType;
    private String name;
    private String phone;
    private String email;
    private String gstin;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String notes;
    private boolean isActive;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
