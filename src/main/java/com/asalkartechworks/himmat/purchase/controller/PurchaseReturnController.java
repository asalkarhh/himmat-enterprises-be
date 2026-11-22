package com.asalkartechworks.himmat.purchase.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseReturnRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseReturnResponseDto;
import com.asalkartechworks.himmat.purchase.service.PurchaseReturnService;
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
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Purchase Returns", description = "Returns to suppliers, outward stock reduction, and credit release")
@SecurityRequirement(name = "Bearer Authentication")
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    @PostMapping("/api/v1/purchases/{purchaseId}/returns")
    @PreAuthorize("hasAnyAuthority('PURCHASE:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create purchase return", description = "Return goods to supplier against an existing purchase, reducing inventory and releasing excess allocations")
    public ResponseEntity<ApiResponse<PurchaseReturnResponseDto>> createReturn(
            @PathVariable Long purchaseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePurchaseReturnRequest request) {
        PurchaseReturnResponseDto response = purchaseReturnService.createReturn(idempotencyKey, purchaseId, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/api/v1/purchases/{purchaseId}/returns")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List returns for purchase", description = "List all purchase returns created against a specific purchase invoice")
    public ResponseEntity<PageResponse<PurchaseReturnResponseDto>> getReturnsForPurchase(
            @PathVariable Long purchaseId,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PurchaseReturnResponseDto> page = purchaseReturnService.searchReturns(null, purchaseId, null, null, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/api/v1/purchase-returns/{returnId}")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get purchase return by ID", description = "Retrieve complete details of a purchase return including lines and refunded amounts")
    public ResponseEntity<ApiResponse<PurchaseReturnResponseDto>> getReturnById(@PathVariable Long returnId) {
        PurchaseReturnResponseDto dto = purchaseReturnService.getReturnById(returnId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @GetMapping("/api/v1/purchase-returns")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search purchase returns", description = "Paginated list of purchase returns across suppliers")
    public ResponseEntity<PageResponse<PurchaseReturnResponseDto>> searchAllReturns(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long purchaseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PurchaseReturnResponseDto> page = purchaseReturnService.searchReturns(supplierId, purchaseId, startDate, endDate, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }
}
