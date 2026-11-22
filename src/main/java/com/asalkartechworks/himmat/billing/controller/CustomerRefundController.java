package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerRefundRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerRefundResponseDto;
import com.asalkartechworks.himmat.billing.service.CustomerRefundService;
import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "Customer Refunds", description = "Disbursement of unallocated customer credit via CASH and ONLINE payment methods")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerRefundController {

    private final CustomerRefundService customerRefundService;

    @PostMapping("/api/v1/customers/{customerId}/refunds")
    @PreAuthorize("hasAnyAuthority('SALES_WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create customer refund", description = "Disburse unallocated customer credit, deducting advance receipts and updating customer ledger")
    public ResponseEntity<ApiResponse<CustomerRefundResponseDto>> createRefund(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable Long customerId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCustomerRefundRequest request) {
        CustomerRefundResponseDto response = customerRefundService.createRefund(
                idempotencyKey, customerId, principal != null ? principal.getId() : 1L, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/api/v1/customers/{customerId}/refunds")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List customer refunds for a customer", description = "Retrieve paginated refund history for a customer")
    public ResponseEntity<ApiResponse<PageResponse<CustomerRefundResponseDto>>> getCustomerRefunds(
            @PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "refundDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CustomerRefundResponseDto> page = customerRefundService.searchRefunds(customerId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @GetMapping("/api/v1/customer-refunds/{refundId}")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer refund by ID", description = "Retrieve a specific refund record with split components and source credit receipts")
    public ResponseEntity<ApiResponse<CustomerRefundResponseDto>> getRefundById(
            @PathVariable Long refundId) {
        CustomerRefundResponseDto refund = customerRefundService.getRefundById(refundId);
        return ResponseEntity.ok(ApiResponse.of(refund));
    }

    @GetMapping("/api/v1/customer-refunds")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search all customer refunds", description = "Search refunds across customers with date range filtering")
    public ResponseEntity<ApiResponse<PageResponse<CustomerRefundResponseDto>>> searchRefunds(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "refundDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CustomerRefundResponseDto> page = customerRefundService.searchRefunds(customerId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.of(page));
    }
}
