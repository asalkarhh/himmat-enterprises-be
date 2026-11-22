package com.asalkartechworks.himmat.purchase.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseResponseDto;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.service.PurchaseService;
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
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
@Tag(name = "Purchases", description = "Supplier purchases, inward goods, and inventory receipt")
@SecurityRequirement(name = "Bearer Authentication")
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PURCHASE:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create inward purchase invoice", description = "Post a purchase invoice from a supplier, atomically receive stock, and record ledger entry")
    public ResponseEntity<ApiResponse<PurchaseResponseDto>> createPurchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePurchaseRequest request) {
        PurchaseResponseDto response = purchaseService.createPurchase(idempotencyKey, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get purchase by ID", description = "Retrieve complete details of a purchase invoice including lines and payment allocation status")
    public ResponseEntity<ApiResponse<PurchaseResponseDto>> getPurchaseById(@PathVariable Long id) {
        PurchaseResponseDto dto = purchaseService.getPurchaseById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search purchases", description = "Paginated list of purchase invoices with supplier, status, date, and invoice search filters")
    public ResponseEntity<PageResponse<PurchaseResponseDto>> searchPurchases(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PurchaseResponseDto> page = purchaseService.searchPurchases(supplierId, status, startDate, endDate, search, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }
}
