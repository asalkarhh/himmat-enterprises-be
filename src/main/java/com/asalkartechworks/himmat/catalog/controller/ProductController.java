package com.asalkartechworks.himmat.catalog.controller;

import com.asalkartechworks.himmat.catalog.dto.CreateProductRequest;
import com.asalkartechworks.himmat.catalog.dto.ProductDto;
import com.asalkartechworks.himmat.catalog.dto.UpdateProductRequest;
import com.asalkartechworks.himmat.catalog.service.ProductService;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product family management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController {

    private final ProductService productService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List products", description = "Paginated list of products with brand, category, subcategory, search, and active filters")
    public ResponseEntity<PageResponse<ProductDto>> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ProductDto> page = productService.getProducts(brandId, categoryId, subcategoryId, search, isActive, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get product by ID", description = "Retrieve a single product family by its unique identifier")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id) {
        ProductDto dto = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create product", description = "Create a new product family under an active brand and subcategory")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateProductRequest request) {
        ProductDto created = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "POST",
                "/api/v1/products",
                request,
                ProductDto.class,
                () -> productService.createProduct(request, principal.getId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Update product", description = "Update product details with optimistic expectedVersion check")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductDto updated = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "PATCH",
                "/api/v1/products/" + id,
                request,
                ProductDto.class,
                () -> productService.updateProduct(id, request, principal.getId())
        );
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
