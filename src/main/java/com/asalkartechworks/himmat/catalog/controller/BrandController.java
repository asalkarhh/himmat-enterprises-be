package com.asalkartechworks.himmat.catalog.controller;

import com.asalkartechworks.himmat.catalog.dto.BrandDto;
import com.asalkartechworks.himmat.catalog.dto.CreateBrandRequest;
import com.asalkartechworks.himmat.catalog.dto.UpdateBrandRequest;
import com.asalkartechworks.himmat.catalog.service.BrandService;
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
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
@Tag(name = "Brands", description = "Brand management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class BrandController {

    private final BrandService brandService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List brands", description = "Paginated list of brands with search and active status filter")
    public ResponseEntity<PageResponse<BrandDto>> getBrands(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<BrandDto> page = brandService.getBrands(search, isActive, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get brand by ID", description = "Retrieve a single brand by its unique identifier")
    public ResponseEntity<ApiResponse<BrandDto>> getBrandById(@PathVariable Long id) {
        BrandDto dto = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create brand", description = "Create a new brand with normalized uniqueness enforcement")
    public ResponseEntity<ApiResponse<BrandDto>> createBrand(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBrandRequest request) {
        BrandDto created = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "POST",
                "/api/v1/brands",
                request,
                BrandDto.class,
                () -> brandService.createBrand(request, principal.getId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Update brand", description = "Update brand details with optimistic expectedVersion check")
    public ResponseEntity<ApiResponse<BrandDto>> updateBrand(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateBrandRequest request) {
        BrandDto updated = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "PATCH",
                "/api/v1/brands/" + id,
                request,
                BrandDto.class,
                () -> brandService.updateBrand(id, request, principal.getId())
        );
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
