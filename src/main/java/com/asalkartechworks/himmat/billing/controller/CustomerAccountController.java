package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.CustomerAccountSummaryDto;
import com.asalkartechworks.himmat.billing.dto.CustomerLedgerStatementDto;
import com.asalkartechworks.himmat.billing.service.CustomerLedgerService;
import com.asalkartechworks.himmat.common.dto.ApiResponse;
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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Accounts & Ledger", description = "Customer account summaries and immutable transaction ledger statements")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerAccountController {

    private final CustomerLedgerService customerLedgerService;

    @GetMapping({"/{customerId}/account", "/{customerId}/account-summary"})
    @PreAuthorize("hasAnyAuthority('CUSTOMER_READ', 'SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer account summary", description = "Current balance, totals invoiced, receipts, returned, refunded, unallocated advance credit, and outstanding dues")
    public ResponseEntity<ApiResponse<CustomerAccountSummaryDto>> getAccountSummary(@PathVariable Long customerId) {
        CustomerAccountSummaryDto summary = customerLedgerService.getAccountSummary(customerId);
        return ResponseEntity.ok(ApiResponse.of(summary));
    }

    @GetMapping("/{customerId}/ledger")
    @PreAuthorize("hasAnyAuthority('CUSTOMER_READ', 'SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get customer ledger statement", description = "Immutable statement with opening balance, chronologically ordered entries with running balances, and closing balance")
    public ResponseEntity<ApiResponse<CustomerLedgerStatementDto>> getLedgerStatement(
            @PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        CustomerLedgerStatementDto statement = customerLedgerService.getLedgerStatement(customerId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.of(statement));
    }
}
