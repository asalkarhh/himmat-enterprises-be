package com.asalkartechworks.himmat.catalog.controller;

import com.asalkartechworks.himmat.catalog.dto.CreateSubcategoryRequest;
import com.asalkartechworks.himmat.catalog.dto.SubcategoryDto;
import com.asalkartechworks.himmat.catalog.dto.UpdateSubcategoryRequest;
import com.asalkartechworks.himmat.catalog.service.SubcategoryService;
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
@RequestMapping("/api/v1/subcategories")
@RequiredArgsConstructor
@Tag(name = "Subcategories", description = "Product subcategory management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class SubcategoryController {

    private final SubcategoryService subcategoryService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List subcategories", description = "Paginated list of subcategories with category, search and active filters")
    public ResponseEntity<PageResponse<SubcategoryDto>> getSubcategories(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<SubcategoryDto> page = subcategoryService.getSubcategories(categoryId, search, isActive, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get subcategory by ID", description = "Retrieve a single subcategory by its unique identifier")
    public ResponseEntity<ApiResponse<SubcategoryDto>> getSubcategoryById(@PathVariable Long id) {
        SubcategoryDto dto = subcategoryService.getSubcategoryById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create subcategory", description = "Create a subcategory under an existing active parent category")
    public ResponseEntity<ApiResponse<SubcategoryDto>> createSubcategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSubcategoryRequest request) {
        SubcategoryDto created = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "POST",
                "/api/v1/subcategories",
                request,
                SubcategoryDto.class,
                () -> subcategoryService.createSubcategory(request, principal.getId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATALOG:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Update subcategory", description = "Update subcategory details with optimistic expectedVersion check")
    public ResponseEntity<ApiResponse<SubcategoryDto>> updateSubcategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateSubcategoryRequest request) {
        SubcategoryDto updated = idempotencyService.executeIdempotent(
                idempotencyKey,
                principal.getId(),
                "PATCH",
                "/api/v1/subcategories/" + id,
                request,
                SubcategoryDto.class,
                () -> subcategoryService.updateSubcategory(id, request, principal.getId())
        );
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
