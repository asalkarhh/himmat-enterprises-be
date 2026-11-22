package com.asalkartechworks.himmat.catalog.controller;

import com.asalkartechworks.himmat.catalog.dto.CategoryDto;
import com.asalkartechworks.himmat.catalog.dto.CreateCategoryRequest;
import com.asalkartechworks.himmat.catalog.dto.UpdateCategoryRequest;
import com.asalkartechworks.himmat.catalog.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category and discount management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class CategoryController {

    private final CategoryService categoryService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List categories", description = "Paginated list of categories with search and active status filter")
    public ResponseEntity<PageResponse<CategoryDto>> getCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<CategoryDto> page = categoryService.getCategories(search, isActive, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get category by ID", description = "Retrieve a single category by its unique identifier")
    public ResponseEntity<ApiResponse<CategoryDto>> getCategoryById(@PathVariable Long id) {
        CategoryDto dto = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create category", description = "Create a category with independent sales and purchase discount percentages")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryDto created = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "POST",
                "/api/v1/categories",
                request,
                CategoryDto.class,
                () -> categoryService.createCategory(request, principal.getId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Update category", description = "Update category name or discount rates with optimistic expectedVersion check")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryDto updated = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "PATCH",
                "/api/v1/categories/" + id,
                request,
                CategoryDto.class,
                () -> categoryService.updateCategory(id, request, principal.getId())
        );
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
