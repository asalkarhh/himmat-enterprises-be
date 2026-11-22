package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.CustomerReturnPreviewResponseDto;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnResponseDto;
import com.asalkartechworks.himmat.billing.service.CustomerReturnService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Customer Returns", description = "Customer return processing, valuation policies, stock reversals, and dues adjustments")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerReturnController {

    private final CustomerReturnService customerReturnService;

    @PostMapping("/api/v1/invoices/{invoiceId}/returns/preview")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Preview customer return", description = "Preview return valuation, line amounts, and released allocation without persisting")
    public ResponseEntity<ApiResponse<CustomerReturnPreviewResponseDto>> previewReturn(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CustomerReturnRequest request) {
        CustomerReturnPreviewResponseDto preview = customerReturnService.previewReturn(invoiceId, request);
        return ResponseEntity.ok(ApiResponse.of(preview));
    }

    @PostMapping("/api/v1/invoices/{invoiceId}/returns")
    @PreAuthorize("hasAnyAuthority('SALES_WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create customer return", description = "Record customer return against an invoice, restoring stock and updating ledger and allocations")
    public ResponseEntity<ApiResponse<CustomerReturnResponseDto>> createReturn(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CustomerReturnRequest request) {
        CustomerReturnResponseDto response = customerReturnService.createReturn(
                idempotencyKey, invoiceId, principal != null ? principal.getId() : 1L, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/api/v1/invoices/{invoiceId}/returns")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List returns for invoice", description = "Retrieve all customer returns created against a specific sales invoice")
    public ResponseEntity<ApiResponse<List<CustomerReturnResponseDto>>> getReturnsByInvoice(
            @PathVariable Long invoiceId) {
        List<CustomerReturnResponseDto> returns = customerReturnService.getReturnsByInvoiceId(invoiceId);
        return ResponseEntity.ok(ApiResponse.of(returns));
    }

    @GetMapping("/api/v1/customer-returns/{returnId}")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer return by ID", description = "Retrieve a specific customer return record")
    public ResponseEntity<ApiResponse<CustomerReturnResponseDto>> getReturnById(
            @PathVariable Long returnId) {
        CustomerReturnResponseDto returnDto = customerReturnService.getReturnById(returnId);
        return ResponseEntity.ok(ApiResponse.of(returnDto));
    }

    @GetMapping("/api/v1/customer-returns")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search customer returns", description = "Search returns with pagination and date range filtering")
    public ResponseEntity<ApiResponse<PageResponse<CustomerReturnResponseDto>>> searchReturns(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "returnDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CustomerReturnResponseDto> page = customerReturnService.searchReturns(customerId, invoiceId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.of(page));
    }
}
