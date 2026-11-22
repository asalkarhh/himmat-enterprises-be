package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.InvoicePreviewRequest;
import com.asalkartechworks.himmat.billing.dto.InvoicePreviewResponseDto;
import com.asalkartechworks.himmat.billing.dto.InvoiceResponseDto;
import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.service.InvoicePreviewService;
import com.asalkartechworks.himmat.billing.service.InvoiceService;
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

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Customer Billing & Invoices", description = "Immediate customer bill creation, previews, and queries")
@SecurityRequirement(name = "Bearer Authentication")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePreviewService invoicePreviewService;

    @PostMapping("/preview")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Preview customer invoice", description = "Read-only calculation of authoritative prices, discounts, GST breakdowns, totals, and pricing fingerprint")
    public ResponseEntity<ApiResponse<InvoicePreviewResponseDto>> previewInvoice(
            @Valid @RequestBody InvoicePreviewRequest request) {
        InvoicePreviewResponseDto preview = invoicePreviewService.previewInvoice(request, true);
        return ResponseEntity.ok(ApiResponse.of(preview));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SALES_WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create customer invoice", description = "Post immediate customer bill, atomically reduce inventory stock, and append to customer ledger")
    public ResponseEntity<ApiResponse<InvoiceResponseDto>> createInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponseDto response = invoiceService.createInvoice(idempotencyKey, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get invoice by ID", description = "Retrieve complete details of a customer sales invoice including lines, settlement status, and receipt allocations")
    public ResponseEntity<ApiResponse<InvoiceResponseDto>> getInvoiceById(@PathVariable Long id) {
        InvoiceResponseDto dto = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search invoices", description = "Paginated list of sales invoices with filters for customer, site, status, dates, overdue, and text search")
    public ResponseEntity<PageResponse<InvoiceResponseDto>> searchInvoices(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) SettlementStatus settlementStatus,
            @RequestParam(required = false) InvoiceDisplayFormat displayFormat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InvoiceResponseDto> page = invoiceService.searchInvoices(
                customerId, siteId, status, settlementStatus, displayFormat,
                startDate, endDate, overdueOnly, search, pageable
        );
        return ResponseEntity.ok(PageResponse.from(page));
    }
}
