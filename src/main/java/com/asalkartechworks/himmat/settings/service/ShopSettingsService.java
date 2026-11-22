package com.asalkartechworks.himmat.settings.service;

import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.settings.dto.ShopSettingsResponse;
import com.asalkartechworks.himmat.settings.dto.UpdateShopSettingsRequest;
import com.asalkartechworks.himmat.settings.model.ShopSettings;
import com.asalkartechworks.himmat.settings.repository.ShopSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopSettingsService {

    private final ShopSettingsRepository shopSettingsRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public ShopSettingsResponse getSettings() {
        ShopSettings settings = shopSettingsRepository.findSingleton()
                .orElseThrow(() -> new ResourceNotFoundException("ShopSettings", String.valueOf(ShopSettings.SINGLETON_ID)));
        return toResponseDto(settings);
    }

    @Transactional
    public ShopSettingsResponse updateSettings(UpdateShopSettingsRequest request, Long currentActorId) {
        ShopSettings settings = shopSettingsRepository.findByIdForUpdate(ShopSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("ShopSettings", String.valueOf(ShopSettings.SINGLETON_ID)));

        // Verify optimistic version match
        if (!settings.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException(
                    "The record changed. Reload before editing.",
                    settings.getVersion()
            );
        }

        // Apply allowlisted updates
        if (request.getName() != null && !request.getName().isBlank()) {
            settings.setName(request.getName().trim());
        }
        if (request.getAddress() != null) {
            settings.setAddress(request.getAddress().trim());
        }
        if (request.getPhone() != null) {
            settings.setPhone(request.getPhone().trim());
        }
        if (request.getEmail() != null) {
            settings.setEmail(request.getEmail().trim());
        }
        if (request.getGstin() != null) {
            settings.setGstin(request.getGstin().trim());
        }
        if (request.getStateCode() != null) {
            settings.setStateCode(request.getStateCode().trim());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            settings.setCurrency(request.getCurrency().trim());
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            settings.setTimezone(request.getTimezone().trim());
        }
        if (request.getCreditDays() != null) {
            settings.setCreditDays(request.getCreditDays());
        }
        if (request.getInvoicePrefix() != null && !request.getInvoicePrefix().isBlank()) {
            settings.setInvoicePrefix(request.getInvoicePrefix().trim());
        }

        settings.setUpdatedAt(businessClock.now());
        settings.setUpdatedBy(currentActorId);

        ShopSettings saved = shopSettingsRepository.save(settings);
        log.info("Updated shop settings version: {}", saved.getVersion());
        return toResponseDto(saved);
    }

    public ShopSettingsResponse toResponseDto(ShopSettings settings) {
        return ShopSettingsResponse.builder()
                .id(settings.getId())
                .name(settings.getName())
                .address(settings.getAddress())
                .phone(settings.getPhone())
                .email(settings.getEmail())
                .gstin(settings.getGstin())
                .stateCode(settings.getStateCode())
                .currency(settings.getCurrency())
                .timezone(settings.getTimezone())
                .creditDays(settings.getCreditDays())
                .invoicePrefix(settings.getInvoicePrefix())
                .version(settings.getVersion())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
