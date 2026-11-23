package com.asalkartechworks.himmat.expense.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.expense.dto.CreateExpenseRequest;
import com.asalkartechworks.himmat.expense.dto.ExpenseResponseDto;
import com.asalkartechworks.himmat.expense.dto.ReverseExpenseRequest;
import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.service.OperationalExpenseService;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
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

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Operational Expenses", description = "Shop operational expenses and bookkeeping reversals")
@SecurityRequirement(name = "Bearer Authentication")
public class OperationalExpenseController {

    private final OperationalExpenseService expenseService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('EXPENSE:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Create operational expense", description = "Post an operational expense with cash/online payment component split")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> createExpense(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponseDto response = expenseService.createExpense(idempotencyKey, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/{expenseId}/reversals")
    @PreAuthorize("hasAnyAuthority('EXPENSE:WRITE', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Reverse operational expense", description = "Perform a bookkeeping reversal on an operational expense")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> reverseExpense(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long expenseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReverseExpenseRequest request) {
        ExpenseResponseDto response = expenseService.reverseExpense(idempotencyKey, expenseId, principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/{expenseId}")
    @PreAuthorize("hasAnyAuthority('EXPENSE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Get expense by ID", description = "Retrieve single operational expense details")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> getExpenseById(@PathVariable Long expenseId) {
        ExpenseResponseDto dto = expenseService.getExpenseById(expenseId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXPENSE:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Search operational expenses", description = "Search and filter operational expenses by date range, category, status, and payment method")
    public ResponseEntity<PageResponse<ExpenseResponseDto>> searchExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<ExpenseResponseDto> response = expenseService.searchExpenses(
                startDate, endDate, category, status, paymentMethod, search, pageable);
        return ResponseEntity.ok(response);
    }
}
