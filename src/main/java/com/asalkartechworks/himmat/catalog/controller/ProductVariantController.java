package com.asalkartechworks.himmat.catalog.controller;

import com.asalkartechworks.himmat.catalog.dto.CreateProductVariantRequest;
import com.asalkartechworks.himmat.catalog.dto.ProductVariantDto;
import com.asalkartechworks.himmat.catalog.dto.UpdateProductVariantRequest;
import com.asalkartechworks.himmat.catalog.service.ProductVariantService;
import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.security.UserPrincipal;
import com.asalkartechworks.himmat.shared.idempotency.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/variants")
@RequiredArgsConstructor
@Tag(name = "Product Variants", description = "Sellable product variant and inventory initialization endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductVariantController {

    private final ProductVariantService productVariantService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List variants", description = "Paginated list of sellable product variants with product, search, and active filters")
    public ResponseEntity<PageResponse<ProductVariantDto>> getVariants(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ProductVariantDto> page = productVariantService.getVariants(productId, search, isActive, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get variant by ID", description = "Retrieve a single product variant by its unique identifier")
    public ResponseEntity<ApiResponse<ProductVariantDto>> getVariantById(@PathVariable Long id) {
        ProductVariantDto dto = productVariantService.getVariantById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create variant", description = "Atomically create product variant and initial inventory balance (quantity: 0)")
    public ResponseEntity<ApiResponse<ProductVariantDto>> createVariant(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateProductVariantRequest request) {
        ProductVariantDto created = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "POST",
                "/api/v1/variants",
                request,
                ProductVariantDto.class,
                () -> productVariantService.createVariant(request, principal.getId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Update variant", description = "Update variant pricing and attributes with optimistic expectedVersion check")
    public ResponseEntity<ApiResponse<ProductVariantDto>> updateVariant(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateProductVariantRequest request) {
        ProductVariantDto updated = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "PATCH",
                "/api/v1/variants/" + id,
                request,
                ProductVariantDto.class,
                () -> productVariantService.updateVariant(id, request, principal.getId())
        );
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
