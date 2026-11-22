package com.asalkartechworks.himmat.purchase.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.purchase.dto.ApplySupplierAllocationRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierPaymentRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierRefundRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentAllocationDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierPaymentResponseDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierRefundResponseDto;
import com.asalkartechworks.himmat.purchase.service.SupplierAllocationService;
import com.asalkartechworks.himmat.purchase.service.SupplierPaymentService;
import com.asalkartechworks.himmat.purchase.service.SupplierRefundService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Supplier Financials", description = "Payments, credit allocations, and refunds for suppliers")
@SecurityRequirement(name = "Bearer Authentication")
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;
    private final SupplierAllocationService supplierAllocationService;
    private final SupplierRefundService supplierRefundService;

    @PostMapping("/{supplierId}/payments")
    @PreAuthorize("hasAnyAuthority('PURCHASE:WRITE', 'SUPPLIER:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Record supplier payment", description = "Record payment to supplier with optional allocation to outstanding purchase invoices")
    public ResponseEntity<ApiResponse<SupplierPaymentResponseDto>> createPayment(
            @PathVariable Long supplierId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSupplierPaymentRequest request) {
        SupplierPaymentResponseDto response = supplierPaymentService.createPayment(idempotencyKey, supplierId, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/{supplierId}/payments")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'SUPPLIER:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List supplier payments", description = "Paginated list of payments made to a specific supplier")
    public ResponseEntity<PageResponse<SupplierPaymentResponseDto>> searchPayments(
            @PathVariable Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SupplierPaymentResponseDto> page = supplierPaymentService.searchPayments(supplierId, startDate, endDate, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @PostMapping("/{supplierId}/allocations")
    @PreAuthorize("hasAnyAuthority('PURCHASE:WRITE', 'SUPPLIER:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Apply unallocated supplier credit", description = "Apply unallocated payment credit to outstanding purchase invoices without duplicating ledger balance effects")
    public ResponseEntity<ApiResponse<List<PaymentAllocationDto>>> applyAllocations(
            @PathVariable Long supplierId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplySupplierAllocationRequest request) {
        List<PaymentAllocationDto> response = supplierAllocationService.applyAllocations(idempotencyKey, supplierId, principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/{supplierId}/refunds")
    @PreAuthorize("hasAnyAuthority('PURCHASE:WRITE', 'SUPPLIER:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Record supplier refund", description = "Record cash/bank refund received from supplier, consuming available unallocated credit")
    public ResponseEntity<ApiResponse<SupplierRefundResponseDto>> createRefund(
            @PathVariable Long supplierId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSupplierRefundRequest request) {
        SupplierRefundResponseDto response = supplierRefundService.createRefund(idempotencyKey, supplierId, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/refunds/{refundId}")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'SUPPLIER:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get supplier refund by ID", description = "Retrieve details of a recorded supplier refund")
    public ResponseEntity<ApiResponse<SupplierRefundResponseDto>> getRefundById(@PathVariable Long refundId) {
        SupplierRefundResponseDto dto = supplierRefundService.getRefundById(refundId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }
}
