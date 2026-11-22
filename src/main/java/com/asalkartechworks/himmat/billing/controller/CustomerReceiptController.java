package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.ApplyCustomerAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReceiptResponseDto;
import com.asalkartechworks.himmat.billing.dto.ReceiptAllocationDto;
import com.asalkartechworks.himmat.billing.service.CustomerAllocationService;
import com.asalkartechworks.himmat.billing.service.CustomerReceiptService;
import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
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
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Customer Receipts & Allocations", description = "Customer payments, advance credit, and invoice allocations")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerReceiptController {

    private final CustomerReceiptService customerReceiptService;
    private final CustomerAllocationService customerAllocationService;

    @PostMapping("/api/v1/customers/{customerId}/receipts")
    @PreAuthorize("hasAnyAuthority('SALES_WRITE', 'SETTLEMENT_WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Record customer receipt", description = "Record payment from customer with optional invoice allocation and ledger entry")
    public ResponseEntity<ApiResponse<CustomerReceiptResponseDto>> createReceipt(
            @PathVariable Long customerId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCustomerReceiptRequest request) {
        CustomerReceiptResponseDto response = customerReceiptService.createReceipt(idempotencyKey, customerId, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/api/v1/customers/{customerId}/receipts")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'SETTLEMENT_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List customer receipts", description = "Paginated list of receipts for a specific customer")
    public ResponseEntity<PageResponse<CustomerReceiptResponseDto>> searchReceipts(
            @PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CustomerReceiptResponseDto> page = customerReceiptService.searchReceipts(customerId, startDate, endDate, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/api/v1/customer-receipts/{receiptId}")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'SETTLEMENT_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get receipt by ID", description = "Retrieve customer receipt details, split components, and invoice allocations")
    public ResponseEntity<ApiResponse<CustomerReceiptResponseDto>> getReceiptById(@PathVariable Long receiptId) {
        CustomerReceiptResponseDto dto = customerReceiptService.getReceiptById(receiptId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @PostMapping("/api/v1/customers/{customerId}/allocations")
    @PreAuthorize("hasAnyAuthority('SALES_WRITE', 'SETTLEMENT_WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Apply unallocated customer credit", description = "Apply unallocated receipt credit to outstanding sales invoices without duplicating ledger balance effects")
    public ResponseEntity<ApiResponse<List<ReceiptAllocationDto>>> applyAllocations(
            @PathVariable Long customerId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplyCustomerAllocationRequest request) {
        List<ReceiptAllocationDto> result = customerAllocationService.applyAllocations(idempotencyKey, customerId, principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.of(result));
    }
}
