package com.asalkartechworks.himmat.inventory.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.inventory.dto.CreateManualAdjustmentRequest;
import com.asalkartechworks.himmat.inventory.dto.InventoryItemDto;
import com.asalkartechworks.himmat.inventory.dto.ManualAdjustmentResponse;
import com.asalkartechworks.himmat.inventory.dto.StockMovementDto;
import com.asalkartechworks.himmat.inventory.dto.StockStatusFilter;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.service.InventoryQueryService;
import com.asalkartechworks.himmat.inventory.service.ManualAdjustmentService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock balance, movements, and manual adjustments")
@SecurityRequirement(name = "Bearer Authentication")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final ManualAdjustmentService manualAdjustmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVENTORY:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List inventory balances", description = "Paginated list of inventory balances with search, brand, category, subcategory, active, and stockStatus filters")
    public ResponseEntity<PageResponse<InventoryItemDto>> getInventory(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StockStatusFilter stockStatus,
            @PageableDefault(sort = "variantId", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<InventoryItemDto> page = inventoryQueryService.getInventory(brandId, categoryId, subcategoryId, isActive, search, stockStatus, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/variants/{variantId}")
    @PreAuthorize("hasAnyAuthority('INVENTORY:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get inventory balance for variant", description = "Retrieve current inventory balance and product hierarchy for a single variant")
    public ResponseEntity<ApiResponse<InventoryItemDto>> getInventoryByVariantId(@PathVariable Long variantId) {
        InventoryItemDto dto = inventoryQueryService.getInventoryByVariantId(variantId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyAuthority('INVENTORY:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List stock movements", description = "Paginated list of immutable stock movements across variants, types, directions, events, and date ranges")
    public ResponseEntity<PageResponse<StockMovementDto>> getMovements(
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) StockMovementDirection direction,
            @RequestParam(required = false) String sourceEntityType,
            @RequestParam(required = false) Long businessEventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockMovementDto> page = inventoryQueryService.getMovements(variantId, movementType, direction, sourceEntityType, businessEventId, fromDate, toDate, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/variants/{variantId}/movements")
    @PreAuthorize("hasAnyAuthority('INVENTORY:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List stock movements for variant", description = "Paginated list of immutable stock movements scoped to a specific variant")
    public ResponseEntity<PageResponse<StockMovementDto>> getVariantMovements(
            @PathVariable Long variantId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) StockMovementDirection direction,
            @RequestParam(required = false) String sourceEntityType,
            @RequestParam(required = false) Long businessEventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockMovementDto> page = inventoryQueryService.getMovements(variantId, movementType, direction, sourceEntityType, businessEventId, fromDate, toDate, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyAuthority('INVENTORY:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Manual stock adjustment", description = "Perform authorized operational stock adjustments (IN/OUT) with optimistic locking and idempotency")
    public ResponseEntity<ApiResponse<ManualAdjustmentResponse>> createAdjustment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateManualAdjustmentRequest request) {
        ManualAdjustmentResponse response = manualAdjustmentService.adjustStock(idempotencyKey, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
