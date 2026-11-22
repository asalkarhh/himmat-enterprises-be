package com.asalkartechworks.himmat.settings.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.security.UserPrincipal;
import com.asalkartechworks.himmat.settings.dto.ShopSettingsResponse;
import com.asalkartechworks.himmat.settings.dto.UpdateShopSettingsRequest;
import com.asalkartechworks.himmat.settings.service.ShopSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/shop")
@RequiredArgsConstructor
@Tag(name = "Shop Settings", description = "Shop profile and operational configurations (Owner & Manager)")
@SecurityRequirement(name = "Bearer Authentication")
public class ShopSettingsController {

    private final ShopSettingsService shopSettingsService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get shop settings", description = "Retrieve current shop settings singleton")
    public ResponseEntity<ApiResponse<ShopSettingsResponse>> getSettings() {
        ShopSettingsResponse settings = shopSettingsService.getSettings();
        return ResponseEntity.ok(ApiResponse.of(settings));
    }

    @PatchMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Update shop settings", description = "Update allowlisted shop configuration fields with expectedVersion concurrency check")
    public ResponseEntity<ApiResponse<ShopSettingsResponse>> updateSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateShopSettingsRequest request) {
        ShopSettingsResponse settings = shopSettingsService.updateSettings(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.of(settings));
    }
}
