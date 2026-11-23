package com.asalkartechworks.himmat.reporting.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.reporting.dto.CashMovementReportDto;
import com.asalkartechworks.himmat.reporting.dto.InventoryReportDto;
import com.asalkartechworks.himmat.reporting.dto.ProfitReportDto;
import com.asalkartechworks.himmat.reporting.dto.PurchaseReportDto;
import com.asalkartechworks.himmat.reporting.dto.SalesReportDto;
import com.asalkartechworks.himmat.reporting.model.ReportingBasis;
import com.asalkartechworks.himmat.reporting.service.ProfitReportService;
import com.asalkartechworks.himmat.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Business, financial, and inventory reports")
@SecurityRequirement(name = "Bearer Authentication")
public class ReportController {

    private final ReportService reportService;
    private final ProfitReportService profitReportService;

    @GetMapping("/sales")
    @PreAuthorize("hasAnyAuthority('REPORT:OPERATIONAL', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Sales report", description = "Comprehensive sales report with ACTIVITY vs DOCUMENT views and customer filtering")
    public ResponseEntity<ApiResponse<SalesReportDto>> getSalesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) ReportingBasis basis,
            @RequestParam(required = false) Long customerId) {

        SalesReportDto report = reportService.getSalesReport(startDate, endDate, basis, customerId);
        return ResponseEntity.ok(ApiResponse.of(report));
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAnyAuthority('REPORT:OPERATIONAL', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Purchases report", description = "Inward purchases, purchase returns, and supplier payments report")
    public ResponseEntity<ApiResponse<PurchaseReportDto>> getPurchaseReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long supplierId) {

        PurchaseReportDto report = reportService.getPurchaseReport(startDate, endDate, supplierId);
        return ResponseEntity.ok(ApiResponse.of(report));
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyAuthority('REPORT:OPERATIONAL', 'INVENTORY:READ', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Inventory report", description = "Current inventory balances, valuations, and zero/low stock indicators")
    public ResponseEntity<ApiResponse<InventoryReportDto>> getInventoryReport(
            @RequestParam(required = false) Boolean zeroStockOnly,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search) {

        InventoryReportDto report = reportService.getInventoryReport(zeroStockOnly, lowStockOnly, brandId, categoryId, search);
        return ResponseEntity.ok(ApiResponse.of(report));
    }

    @GetMapping("/cash-movements")
    @PreAuthorize("hasAnyAuthority('REPORT:OPERATIONAL', 'ROLE_OWNER', 'ROLE_MANAGER')")
    @Operation(summary = "Cash movements report", description = "Real cash and online monetary movements (receipts, payments, refunds, and operating expenses)")
    public ResponseEntity<ApiResponse<CashMovementReportDto>> getCashMovementReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) PaymentMethod paymentMethod) {

        CashMovementReportDto report = reportService.getCashMovementReport(startDate, endDate, paymentMethod);
        return ResponseEntity.ok(ApiResponse.of(report));
    }

    @GetMapping("/profit")
    @PreAuthorize("hasAuthority('REPORT:PROFIT') or hasRole('OWNER')")
    @Operation(summary = "Owner profit report", description = "Owner-only gross and net profit report based on immutable transaction line cost snapshots")
    public ResponseEntity<ApiResponse<ProfitReportDto>> getProfitReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId) {

        ProfitReportDto report = profitReportService.getProfitReport(startDate, endDate, variantId, productId, brandId, categoryId);
        return ResponseEntity.ok(ApiResponse.of(report));
    }

    @GetMapping("/profit/products")
    @PreAuthorize("hasAuthority('REPORT:PROFIT') or hasRole('OWNER')")
    @Operation(summary = "Owner product profit breakdown", description = "Owner-only product level profit and margin breakdown")
    public ResponseEntity<ApiResponse<List<ProfitReportDto.ProductProfitItemDto>>> getProductProfitReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId) {

        ProfitReportDto report = profitReportService.getProfitReport(startDate, endDate, variantId, productId, brandId, categoryId);
        return ResponseEntity.ok(ApiResponse.of(report.getProductBreakdown()));
    }
}
