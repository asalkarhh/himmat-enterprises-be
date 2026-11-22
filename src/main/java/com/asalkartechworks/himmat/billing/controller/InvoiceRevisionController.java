package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionPreviewResponseDto;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionResponseDto;
import com.asalkartechworks.himmat.billing.service.InvoiceRevisionService;
import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/revisions")
@RequiredArgsConstructor
@Tag(name = "Invoice Revisions", description = "Versioned bill corrections, line updates, and snapshot audit trails")
@SecurityRequirement(name = "Bearer Authentication")
public class InvoiceRevisionController {

    private final InvoiceRevisionService invoiceRevisionService;

    @PostMapping("/preview")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Preview invoice revision", description = "Preview line differences, delta payable, and excess allocation release without persisting changes")
    public ResponseEntity<ApiResponse<InvoiceRevisionPreviewResponseDto>> previewRevision(
            @PathVariable Long invoiceId,
            @Valid @RequestBody InvoiceRevisionRequest request) {
        InvoiceRevisionPreviewResponseDto preview = invoiceRevisionService.previewRevision(invoiceId, request);
        return ResponseEntity.ok(ApiResponse.of(preview));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SALES_WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create invoice revision", description = "Apply authoritative versioned revision to a sales invoice, adjusting stock, ledger, and allocations")
    public ResponseEntity<ApiResponse<InvoiceRevisionResponseDto>> createRevision(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InvoiceRevisionRequest request) {
        InvoiceRevisionResponseDto response = invoiceRevisionService.createRevision(
                idempotencyKey, invoiceId, principal != null ? principal.getId() : 1L, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "List revisions for invoice", description = "Retrieve all historical revision snapshots for a given sales invoice")
    public ResponseEntity<ApiResponse<List<InvoiceRevisionResponseDto>>> getRevisions(
            @PathVariable Long invoiceId) {
        List<InvoiceRevisionResponseDto> revisions = invoiceRevisionService.getRevisionsByInvoiceId(invoiceId);
        return ResponseEntity.ok(ApiResponse.of(revisions));
    }

    @GetMapping("/{revisionId}")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get invoice revision by ID", description = "Retrieve a specific revision record with line diffs and snapshot payload")
    public ResponseEntity<ApiResponse<InvoiceRevisionResponseDto>> getRevisionById(
            @PathVariable Long invoiceId,
            @PathVariable Long revisionId) {
        InvoiceRevisionResponseDto revision = invoiceRevisionService.getRevisionById(revisionId);
        return ResponseEntity.ok(ApiResponse.of(revision));
    }
}
