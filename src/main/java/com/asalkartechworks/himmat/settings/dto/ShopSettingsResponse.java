package com.asalkartechworks.himmat.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopSettingsResponse {

    private Long id;
    private String name;
    private String address;
    private String phone;
    private String email;
    private String gstin;
    private String stateCode;
    private String currency;
    private String timezone;
    private Integer creditDays;
    private String invoicePrefix;
    private Long version;
    private Instant updatedAt;
}
