package com.asalkartechworks.himmat.pricing.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewResponse;
import com.asalkartechworks.himmat.pricing.service.PricingService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Price calculation and preview endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/preview")
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'SALES:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Calculate pricing preview", description = "Authoritative read-only pricing calculation with discounts, GST breakdown, and rounding")
    public ResponseEntity<ApiResponse<PricingPreviewResponse>> calculatePreview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PricingPreviewRequest request) {
        boolean canViewProfit = principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("REPORT:PROFIT") || a.getAuthority().equals("ROLE_OWNER"));

        PricingPreviewResponse response = pricingService.calculatePreview(request, canViewProfit);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
