package com.asalkartechworks.himmat.purchase.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.purchase.dto.SupplierAccountSummaryDto;
import com.asalkartechworks.himmat.purchase.dto.SupplierLedgerStatementDto;
import com.asalkartechworks.himmat.purchase.service.SupplierLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Supplier Accounts & Ledger", description = "Supplier account summaries and immutable transaction ledger statements")
@SecurityRequirement(name = "Bearer Authentication")
public class SupplierAccountController {

    private final SupplierLedgerService supplierLedgerService;

    @GetMapping("/{supplierId}/account-summary")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'SUPPLIER:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get supplier account summary", description = "Current balance, totals purchased, paid, returned, refunded, and outstanding balance")
    public ResponseEntity<ApiResponse<SupplierAccountSummaryDto>> getAccountSummary(@PathVariable Long supplierId) {
        SupplierAccountSummaryDto summary = supplierLedgerService.getAccountSummary(supplierId);
        return ResponseEntity.ok(ApiResponse.of(summary));
    }

    @GetMapping("/{supplierId}/ledger")
    @PreAuthorize("hasAnyAuthority('PURCHASE:READ', 'SUPPLIER:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get supplier ledger statement", description = "Immutable statement with opening balance, chronologically ordered entries with running balances, and closing balance")
    public ResponseEntity<ApiResponse<SupplierLedgerStatementDto>> getLedgerStatement(
            @PathVariable Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        SupplierLedgerStatementDto statement = supplierLedgerService.getLedgerStatement(supplierId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.of(statement));
    }
}
