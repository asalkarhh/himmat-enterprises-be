package com.asalkartechworks.himmat.billing.controller;

import com.asalkartechworks.himmat.billing.dto.DueInvoiceDto;
import com.asalkartechworks.himmat.billing.dto.DuesSummaryDto;
import com.asalkartechworks.himmat.billing.service.DuesService;
import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dues")
@RequiredArgsConstructor
@Tag(name = "Customer Dues & Overdues", description = "Outstanding balances, aging, and overdue invoices")
@SecurityRequirement(name = "Bearer Authentication")
public class DuesController {

    private final DuesService duesService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search due and overdue invoices", description = "Paginated list of posted invoices with outstanding balances, overdue flags, and days overdue")
    public ResponseEntity<PageResponse<DueInvoiceDto>> searchDueInvoices(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueEndDate,
            @RequestParam(required = false) Boolean overdueOnly,
            @PageableDefault(sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<DueInvoiceDto> response = duesService.searchDueInvoices(
                customerId, siteId, dueStartDate, dueEndDate, overdueOnly, pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('SALES_READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get dues summary", description = "Total outstanding amount, total overdue amount, and invoice counts across filtered invoices")
    public ResponseEntity<ApiResponse<DuesSummaryDto>> getDuesSummary(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueEndDate,
            @RequestParam(required = false) Boolean overdueOnly) {
        DuesSummaryDto summary = duesService.getDuesSummary(customerId, siteId, dueStartDate, dueEndDate, overdueOnly);
        return ResponseEntity.ok(ApiResponse.of(summary));
    }
}
