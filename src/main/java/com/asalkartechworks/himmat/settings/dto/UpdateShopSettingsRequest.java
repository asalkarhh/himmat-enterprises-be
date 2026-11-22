package com.asalkartechworks.himmat.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
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
public class UpdateShopSettingsRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @NotBlank(message = "Shop name is required")
    @Size(max = 200, message = "Shop name must not exceed 200 characters")
    private String name;

    private String address;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 20, message = "GSTIN must not exceed 20 characters")
    private String gstin;

    @Size(max = 10, message = "State code must not exceed 10 characters")
    private String stateCode;

    @Size(max = 10, message = "Currency must not exceed 10 characters")
    private String currency;

    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    private String timezone;

    @Min(value = 0, message = "Credit days must be non-negative")
    private Integer creditDays;

    @Size(max = 20, message = "Invoice prefix must not exceed 20 characters")
    private String invoicePrefix;
}
