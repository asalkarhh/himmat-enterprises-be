package com.asalkartechworks.himmat.reporting.service;

import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerReceiptComponent;
import com.asalkartechworks.himmat.billing.model.CustomerRefund;
import com.asalkartechworks.himmat.billing.model.CustomerRefundComponent;
import com.asalkartechworks.himmat.billing.model.CustomerReturn;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevision;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerRefundRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReturnRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionRepository;
import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.model.OperationalExpense;
import com.asalkartechworks.himmat.expense.model.OperationalExpenseComponent;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseRepository;
import com.asalkartechworks.himmat.purchase.model.PaymentComponent;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseReturn;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.model.SupplierRefund;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseReturnRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierRefundRepository;
import com.asalkartechworks.himmat.reporting.dto.CashMovementReportDto;
import com.asalkartechworks.himmat.reporting.dto.InventoryReportDto;
import com.asalkartechworks.himmat.reporting.dto.PurchaseReportDto;
import com.asalkartechworks.himmat.reporting.dto.SalesReportDto;
import com.asalkartechworks.himmat.reporting.model.ReportingBasis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceRevisionRepository salesInvoiceRevisionRepository;
    private final CustomerReturnRepository customerReturnRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerRefundRepository customerRefundRepository;

    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierRefundRepository supplierRefundRepository;

    private final OperationalExpenseRepository operationalExpenseRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public SalesReportDto getSalesReport(LocalDate startDate, LocalDate endDate, ReportingBasis basis, Long customerId) {
        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();
        ReportingBasis effectiveBasis = basis != null ? basis : ReportingBasis.ACTIVITY;

        List<SalesReportDto.SalesRowItemDto> items = new ArrayList<>();
        BigDecimal totalGrossSales = BigDecimal.ZERO;
        BigDecimal totalRevisionsDelta = BigDecimal.ZERO;
        BigDecimal totalReturnsCredit = BigDecimal.ZERO;
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal unknownTaxSales = BigDecimal.ZERO;

        if (effectiveBasis == ReportingBasis.ACTIVITY) {
            // 1. Original invoices in date range
            List<SalesInvoice> invoices = salesInvoiceRepository.findByStatusAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                    InvoiceStatus.POSTED, effectiveStart, effectiveEnd);

            for (SalesInvoice inv : invoices) {
                if (customerId != null && !inv.getCustomer().getId().equals(customerId)) continue;

                BigDecimal gross = inv.getOriginalPayableAmount() != null ? inv.getOriginalPayableAmount() : inv.getTotalPayableAmount();
                totalGrossSales = totalGrossSales.add(gross);

                if (inv.getTotalTaxableAmount() != null) {
                    totalTaxable = totalTaxable.add(inv.getTotalTaxableAmount());
                } else {
                    unknownTaxSales = unknownTaxSales.add(gross);
                }
                if (inv.getTotalGstAmount() != null) {
                    totalTax = totalTax.add(inv.getTotalGstAmount());
                }

                items.add(SalesReportDto.SalesRowItemDto.builder()
                        .documentNumber(inv.getInvoiceNumber())
                        .documentDate(inv.getInvoiceDate())
                        .customerId(inv.getCustomer().getId())
                        .customerName(inv.getCustomerName())
                        .type("INVOICE")
                        .grossOrDeltaAmount(gross)
                        .returnsAmount(BigDecimal.ZERO)
                        .netAmount(gross)
                        .taxableAmount(inv.getTotalTaxableAmount())
                        .taxAmount(inv.getTotalGstAmount())
                        .build());
            }

            // 2. Revisions in date range
            Instant startInstant = effectiveStart.atStartOfDay(BusinessClock.BUSINESS_ZONE).toInstant();
            Instant endInstant = effectiveEnd.plusDays(1).atStartOfDay(BusinessClock.BUSINESS_ZONE).toInstant();
            List<SalesInvoiceRevision> revisions = salesInvoiceRevisionRepository.findByCreatedAtBetween(startInstant, endInstant);

            for (SalesInvoiceRevision rev : revisions) {
                SalesInvoice inv = rev.getInvoice();
                if (customerId != null && !inv.getCustomer().getId().equals(customerId)) continue;

                BigDecimal delta = rev.getDeltaPayableAmount();
                totalRevisionsDelta = totalRevisionsDelta.add(delta);

                BigDecimal deltaTaxable = (rev.getNewTotalTaxable() != null && rev.getPreviousTotalTaxable() != null)
                        ? rev.getNewTotalTaxable().subtract(rev.getPreviousTotalTaxable()) : BigDecimal.ZERO;
                BigDecimal deltaTax = (rev.getNewTotalGst() != null && rev.getPreviousTotalGst() != null)
                        ? rev.getNewTotalGst().subtract(rev.getPreviousTotalGst()) : BigDecimal.ZERO;

                totalTaxable = totalTaxable.add(deltaTaxable);
                totalTax = totalTax.add(deltaTax);

                LocalDate revDate = LocalDate.ofInstant(rev.getCreatedAt(), BusinessClock.BUSINESS_ZONE);

                items.add(SalesReportDto.SalesRowItemDto.builder()
                        .documentNumber(inv.getInvoiceNumber() + "-R" + rev.getRevisionNumber())
                        .documentDate(revDate)
                        .customerId(inv.getCustomer().getId())
                        .customerName(inv.getCustomerName())
                        .type("REVISION")
                        .grossOrDeltaAmount(delta)
                        .returnsAmount(BigDecimal.ZERO)
                        .netAmount(delta)
                        .taxableAmount(deltaTaxable)
                        .taxAmount(deltaTax)
                        .build());
            }

            // 3. Customer returns in date range
            List<CustomerReturn> returns = customerReturnRepository.findByReturnDateBetweenOrderByReturnDateAsc(
                    effectiveStart, effectiveEnd);

            for (CustomerReturn ret : returns) {
                if (customerId != null && !ret.getCustomer().getId().equals(customerId)) continue;

                BigDecimal retAmount = ret.getTotalReturnAmount();
                totalReturnsCredit = totalReturnsCredit.add(retAmount);

                BigDecimal retTaxable = BigDecimal.ZERO;
                BigDecimal retTax = BigDecimal.ZERO;
                if (ret.getLines() != null) {
                    for (var line : ret.getLines()) {
                        if (line.getTaxableAmount() != null) retTaxable = retTaxable.add(line.getTaxableAmount());
                        if (line.getTaxAmount() != null) retTax = retTax.add(line.getTaxAmount());
                    }
                }

                totalTaxable = totalTaxable.subtract(retTaxable);
                totalTax = totalTax.subtract(retTax);

                items.add(SalesReportDto.SalesRowItemDto.builder()
                        .documentNumber(ret.getReturnNumber())
                        .documentDate(ret.getReturnDate())
                        .customerId(ret.getCustomer().getId())
                        .customerName(ret.getCustomer().getName())
                        .type("RETURN")
                        .grossOrDeltaAmount(retAmount.negate())
                        .returnsAmount(retAmount)
                        .netAmount(retAmount.negate())
                        .taxableAmount(retTaxable.negate())
                        .taxAmount(retTax.negate())
                        .build());
            }

            // Sort chronologically
            items.sort(Comparator.comparing(SalesReportDto.SalesRowItemDto::getDocumentDate)
                    .thenComparing(SalesReportDto.SalesRowItemDto::getDocumentNumber));

        } else {
            // DOCUMENT basis: Current effective state of invoices issued in date range
            List<SalesInvoice> invoices = salesInvoiceRepository.findByStatusAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                    InvoiceStatus.POSTED, effectiveStart, effectiveEnd);

            for (SalesInvoice inv : invoices) {
                if (customerId != null && !inv.getCustomer().getId().equals(customerId)) continue;

                BigDecimal originalPayable = inv.getOriginalPayableAmount() != null ? inv.getOriginalPayableAmount() : inv.getTotalPayableAmount();
                BigDecimal returnedAmount = inv.getTotalReturnedAmount() != null ? inv.getTotalReturnedAmount() : BigDecimal.ZERO;
                BigDecimal netAmount = inv.getTotalPayableAmount().subtract(returnedAmount);

                totalGrossSales = totalGrossSales.add(originalPayable);
                BigDecimal revisionDiff = inv.getTotalPayableAmount().subtract(originalPayable);
                totalRevisionsDelta = totalRevisionsDelta.add(revisionDiff);
                totalReturnsCredit = totalReturnsCredit.add(returnedAmount);

                if (inv.getTotalTaxableAmount() != null) {
                    totalTaxable = totalTaxable.add(inv.getTotalTaxableAmount());
                } else {
                    unknownTaxSales = unknownTaxSales.add(originalPayable);
                }
                if (inv.getTotalGstAmount() != null) {
                    totalTax = totalTax.add(inv.getTotalGstAmount());
                }

                items.add(SalesReportDto.SalesRowItemDto.builder()
                        .documentNumber(inv.getInvoiceNumber())
                        .documentDate(inv.getInvoiceDate())
                        .customerId(inv.getCustomer().getId())
                        .customerName(inv.getCustomerName())
                        .type("INVOICE")
                        .grossOrDeltaAmount(originalPayable)
                        .returnsAmount(returnedAmount)
                        .netAmount(netAmount)
                        .taxableAmount(inv.getTotalTaxableAmount())
                        .taxAmount(inv.getTotalGstAmount())
                        .build());
            }
        }

        BigDecimal totalNetSales = totalGrossSales.add(totalRevisionsDelta).subtract(totalReturnsCredit);

        return SalesReportDto.builder()
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .basis(effectiveBasis)
                .totalGrossSales(MoneyPolicy.roundMoney(totalGrossSales))
                .totalRevisionsDelta(MoneyPolicy.roundMoney(totalRevisionsDelta))
                .totalReturnsCredit(MoneyPolicy.roundMoney(totalReturnsCredit))
                .totalNetSales(MoneyPolicy.roundMoney(totalNetSales))
                .totalTaxableAmount(MoneyPolicy.roundMoney(totalTaxable))
                .totalTaxAmount(MoneyPolicy.roundMoney(totalTax))
                .unknownTaxSales(MoneyPolicy.roundMoney(unknownTaxSales))
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public PurchaseReportDto getPurchaseReport(LocalDate startDate, LocalDate endDate, Long supplierId) {
        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();

        List<PurchaseReportDto.PurchaseRowItemDto> items = new ArrayList<>();
        BigDecimal totalGrossPurchases = BigDecimal.ZERO;
        BigDecimal totalPurchaseReturns = BigDecimal.ZERO;
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        List<Purchase> purchases = purchaseRepository.findByStatusAndPurchaseDateBetweenOrderByPurchaseDateAsc(
                PurchaseStatus.POSTED, effectiveStart, effectiveEnd);

        for (Purchase p : purchases) {
            if (supplierId != null && !p.getSupplier().getId().equals(supplierId)) continue;

            BigDecimal payable = p.getTotalPayableAmount();
            totalGrossPurchases = totalGrossPurchases.add(payable);

            if (p.getSubtotalExclTax() != null) {
                totalTaxable = totalTaxable.add(p.getSubtotalExclTax());
            }
            if (p.getTotalGstAmount() != null) {
                totalTax = totalTax.add(p.getTotalGstAmount());
            }

            items.add(PurchaseReportDto.PurchaseRowItemDto.builder()
                    .documentNumber(p.getPurchaseNumber())
                    .documentDate(p.getPurchaseDate())
                    .supplierId(p.getSupplier().getId())
                    .supplierName(p.getSupplierName())
                    .type("PURCHASE")
                    .supplierInvoiceNumber(p.getSupplierInvoiceNumber())
                    .grossOrDeltaAmount(payable)
                    .taxableAmount(p.getSubtotalExclTax())
                    .taxAmount(p.getTotalGstAmount())
                    .build());
        }

        List<PurchaseReturn> returns = purchaseReturnRepository.findByReturnDateBetweenOrderByReturnDateAsc(
                effectiveStart, effectiveEnd);

        for (PurchaseReturn pr : returns) {
            if (supplierId != null && !pr.getSupplier().getId().equals(supplierId)) continue;

            BigDecimal retAmount = pr.getTotalReturnAmount();
            totalPurchaseReturns = totalPurchaseReturns.add(retAmount);

            BigDecimal retTaxable = BigDecimal.ZERO;
            BigDecimal retTax = BigDecimal.ZERO;
            if (pr.getLines() != null) {
                for (var line : pr.getLines()) {
                    if (line.getTotalAmount() != null) {
                        retTaxable = retTaxable.add(line.getTotalAmount());
                    }
                }
            }

            totalTaxable = totalTaxable.subtract(retTaxable);
            totalTax = totalTax.subtract(retTax);

            items.add(PurchaseReportDto.PurchaseRowItemDto.builder()
                    .documentNumber(pr.getReturnNumber())
                    .documentDate(pr.getReturnDate())
                    .supplierId(pr.getSupplier().getId())
                    .supplierName(pr.getSupplier().getName())
                    .type("PURCHASE_RETURN")
                    .supplierInvoiceNumber(null)
                    .grossOrDeltaAmount(retAmount.negate())
                    .taxableAmount(retTaxable.negate())
                    .taxAmount(retTax.negate())
                    .build());
        }

        items.sort(Comparator.comparing(PurchaseReportDto.PurchaseRowItemDto::getDocumentDate)
                .thenComparing(PurchaseReportDto.PurchaseRowItemDto::getDocumentNumber));

        BigDecimal totalNetPurchases = totalGrossPurchases.subtract(totalPurchaseReturns);

        List<SupplierPayment> payments = supplierPaymentRepository.findByPaymentDateBetweenOrderByPaymentDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal totalPayments = payments.stream()
                .filter(p -> supplierId == null || p.getSupplier().getId().equals(supplierId))
                .map(SupplierPayment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SupplierRefund> refunds = supplierRefundRepository.findByRefundDateBetweenOrderByRefundDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal totalRefunds = refunds.stream()
                .filter(r -> supplierId == null || r.getSupplier().getId().equals(supplierId))
                .map(SupplierRefund::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PurchaseReportDto.builder()
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .totalGrossPurchases(MoneyPolicy.roundMoney(totalGrossPurchases))
                .totalPurchaseReturns(MoneyPolicy.roundMoney(totalPurchaseReturns))
                .totalNetPurchases(MoneyPolicy.roundMoney(totalNetPurchases))
                .totalSupplierPayments(MoneyPolicy.roundMoney(totalPayments))
                .totalSupplierRefunds(MoneyPolicy.roundMoney(totalRefunds))
                .totalTaxableAmount(MoneyPolicy.roundMoney(totalTaxable))
                .totalTaxAmount(MoneyPolicy.roundMoney(totalTax))
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryReportDto getInventoryReport(Boolean zeroStockOnly, Boolean lowStockOnly, Long brandId, Long categoryId, String search) {
        Page<InventoryBalance> balances = inventoryBalanceRepository.searchInventory(
                brandId, categoryId, null, true, search, null, Pageable.unpaged());

        List<InventoryReportDto.InventoryReportRowDto> items = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalValuation = BigDecimal.ZERO;
        int zeroCount = 0;
        int lowCount = 0;

        for (InventoryBalance ib : balances) {
            ProductVariant variant = ib.getVariant();
            int qty = ib.getQuantityOnHand() != null ? ib.getQuantityOnHand() : 0;
            int threshold = variant.getLowStockThreshold() != null ? variant.getLowStockThreshold() : 0;

            String status;
            if (qty <= 0) {
                status = "OUT_OF_STOCK";
                zeroCount++;
            } else if ((threshold > 0 && qty <= threshold) || (threshold == 0 && qty <= 5)) {
                status = "LOW_STOCK";
                lowCount++;
            } else {
                status = "IN_STOCK";
            }

            if (Boolean.TRUE.equals(zeroStockOnly) && qty > 0) continue;
            if (Boolean.TRUE.equals(lowStockOnly) && !"LOW_STOCK".equals(status)) continue;

            totalQuantity += qty;

            BigDecimal valuation = null;
            boolean costAvailable = variant.getPurchaseCostExclTax() != null;
            if (costAvailable && qty > 0) {
                valuation = variant.getPurchaseCostExclTax().multiply(BigDecimal.valueOf(qty));
                totalValuation = totalValuation.add(valuation);
            }

            items.add(InventoryReportDto.InventoryReportRowDto.builder()
                    .variantId(variant.getId())
                    .productId(variant.getProduct().getId())
                    .sku(variant.getSku())
                    .productName(variant.getProduct().getName())
                    .sizeLabel(variant.getSizeLabel())
                    .brandName(variant.getProduct().getBrand() != null ? variant.getProduct().getBrand().getName() : "")
                    .categoryName(variant.getProduct().getSubcategory() != null && variant.getProduct().getSubcategory().getCategory() != null
                            ? variant.getProduct().getSubcategory().getCategory().getName() : "")
                    .subcategoryName(variant.getProduct().getSubcategory() != null ? variant.getProduct().getSubcategory().getName() : "")
                    .currentQuantity(qty)
                    .reorderLevel(threshold)
                    .stockStatus(status)
                    .purchaseCostExclTax(variant.getPurchaseCostExclTax())
                    .costAvailable(costAvailable)
                    .valuationAmount(valuation != null ? MoneyPolicy.roundMoney(valuation) : null)
                    .build());
        }

        return InventoryReportDto.builder()
                .totalVariants(items.size())
                .totalStockQuantity(totalQuantity)
                .totalStockValuation(MoneyPolicy.roundMoney(totalValuation))
                .zeroStockCount(zeroCount)
                .lowStockCount(lowCount)
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public CashMovementReportDto getCashMovementReport(LocalDate startDate, LocalDate endDate, PaymentMethod paymentMethod) {
        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();

        List<CashMovementReportDto.CashMovementItemDto> items = new ArrayList<>();
        BigDecimal totalCashIn = BigDecimal.ZERO;
        BigDecimal totalCashOut = BigDecimal.ZERO;
        BigDecimal totalOnlineIn = BigDecimal.ZERO;
        BigDecimal totalOnlineOut = BigDecimal.ZERO;

        // IN: Customer Receipts
        List<CustomerReceipt> receipts = customerReceiptRepository.findByReceiptDateBetweenOrderByReceiptDateAsc(
                effectiveStart, effectiveEnd);
        for (CustomerReceipt r : receipts) {
            for (CustomerReceiptComponent comp : r.getComponents()) {
                if (paymentMethod != null && comp.getMethod() != paymentMethod) continue;

                if (comp.getMethod() == PaymentMethod.CASH) {
                    totalCashIn = totalCashIn.add(comp.getAmount());
                } else {
                    totalOnlineIn = totalOnlineIn.add(comp.getAmount());
                }

                items.add(CashMovementReportDto.CashMovementItemDto.builder()
                        .date(r.getReceiptDate())
                        .movementType("IN")
                        .sourceType("CUSTOMER_RECEIPT")
                        .sourceNumber(r.getReceiptNumber())
                        .partyName(r.getCustomer().getName())
                        .method(comp.getMethod())
                        .amount(comp.getAmount())
                        .reference(comp.getOnlineReference())
                        .build());
            }
        }

        // IN: Supplier Refunds
        List<SupplierRefund> supplierRefunds = supplierRefundRepository.findByRefundDateBetweenOrderByRefundDateAsc(
                effectiveStart, effectiveEnd);
        for (SupplierRefund r : supplierRefunds) {
            for (PaymentComponent comp : r.getComponents()) {
                if (paymentMethod != null && comp.getMethod() != paymentMethod) continue;

                if (comp.getMethod() == PaymentMethod.CASH) {
                    totalCashIn = totalCashIn.add(comp.getAmount());
                } else {
                    totalOnlineIn = totalOnlineIn.add(comp.getAmount());
                }

                items.add(CashMovementReportDto.CashMovementItemDto.builder()
                        .date(r.getRefundDate())
                        .movementType("IN")
                        .sourceType("SUPPLIER_REFUND")
                        .sourceNumber(r.getRefundNumber())
                        .partyName(r.getSupplier().getName())
                        .method(comp.getMethod())
                        .amount(comp.getAmount())
                        .reference(comp.getOnlineReference())
                        .build());
            }
        }

        // OUT: Supplier Payments
        List<SupplierPayment> payments = supplierPaymentRepository.findByPaymentDateBetweenOrderByPaymentDateAsc(
                effectiveStart, effectiveEnd);
        for (SupplierPayment p : payments) {
            for (PaymentComponent comp : p.getComponents()) {
                if (paymentMethod != null && comp.getMethod() != paymentMethod) continue;

                if (comp.getMethod() == PaymentMethod.CASH) {
                    totalCashOut = totalCashOut.add(comp.getAmount());
                } else {
                    totalOnlineOut = totalOnlineOut.add(comp.getAmount());
                }

                items.add(CashMovementReportDto.CashMovementItemDto.builder()
                        .date(p.getPaymentDate())
                        .movementType("OUT")
                        .sourceType("SUPPLIER_PAYMENT")
                        .sourceNumber(p.getPaymentNumber())
                        .partyName(p.getSupplier().getName())
                        .method(comp.getMethod())
                        .amount(comp.getAmount())
                        .reference(comp.getOnlineReference())
                        .build());
            }
        }

        // OUT: Customer Refunds
        List<CustomerRefund> customerRefunds = customerRefundRepository.findByRefundDateBetweenOrderByRefundDateAsc(
                effectiveStart, effectiveEnd);
        for (CustomerRefund cr : customerRefunds) {
            for (CustomerRefundComponent comp : cr.getComponents()) {
                if (paymentMethod != null && comp.getMethod() != paymentMethod) continue;

                if (comp.getMethod() == PaymentMethod.CASH) {
                    totalCashOut = totalCashOut.add(comp.getAmount());
                } else {
                    totalOnlineOut = totalOnlineOut.add(comp.getAmount());
                }

                items.add(CashMovementReportDto.CashMovementItemDto.builder()
                        .date(cr.getRefundDate())
                        .movementType("OUT")
                        .sourceType("CUSTOMER_REFUND")
                        .sourceNumber(cr.getRefundNumber())
                        .partyName(cr.getCustomer().getName())
                        .method(comp.getMethod())
                        .amount(comp.getAmount())
                        .reference(comp.getOnlineReference())
                        .build());
            }
        }

        // OUT: Active Operational Expenses (Reversals soft/excluded)
        List<OperationalExpense> expenses = operationalExpenseRepository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(
                ExpenseStatus.POSTED, effectiveStart, effectiveEnd);
        for (OperationalExpense exp : expenses) {
            for (OperationalExpenseComponent comp : exp.getComponents()) {
                if (paymentMethod != null && comp.getMethod() != paymentMethod) continue;

                if (comp.getMethod() == PaymentMethod.CASH) {
                    totalCashOut = totalCashOut.add(comp.getAmount());
                } else {
                    totalOnlineOut = totalOnlineOut.add(comp.getAmount());
                }

                items.add(CashMovementReportDto.CashMovementItemDto.builder()
                        .date(exp.getExpenseDate())
                        .movementType("OUT")
                        .sourceType("OPERATIONAL_EXPENSE")
                        .sourceNumber(exp.getExpenseNumber())
                        .partyName(exp.getDescription() + " (" + exp.getCategory() + ")")
                        .method(comp.getMethod())
                        .amount(comp.getAmount())
                        .reference(comp.getOnlineReference())
                        .build());
            }
        }

        // Sort chronologically descending
        items.sort(Comparator.comparing(CashMovementReportDto.CashMovementItemDto::getDate).reversed()
                .thenComparing(CashMovementReportDto.CashMovementItemDto::getSourceNumber));

        BigDecimal netCash = totalCashIn.subtract(totalCashOut);
        BigDecimal netOnline = totalOnlineIn.subtract(totalOnlineOut);
        BigDecimal grandIn = totalCashIn.add(totalOnlineIn);
        BigDecimal grandOut = totalCashOut.add(totalOnlineOut);
        BigDecimal netCashflow = grandIn.subtract(grandOut);

        return CashMovementReportDto.builder()
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .totalCashIn(MoneyPolicy.roundMoney(totalCashIn))
                .totalCashOut(MoneyPolicy.roundMoney(totalCashOut))
                .netCashMovement(MoneyPolicy.roundMoney(netCash))
                .totalOnlineIn(MoneyPolicy.roundMoney(totalOnlineIn))
                .totalOnlineOut(MoneyPolicy.roundMoney(totalOnlineOut))
                .netOnlineMovement(MoneyPolicy.roundMoney(netOnline))
                .grandTotalIn(MoneyPolicy.roundMoney(grandIn))
                .grandTotalOut(MoneyPolicy.roundMoney(grandOut))
                .netCashflow(MoneyPolicy.roundMoney(netCashflow))
                .items(items)
                .build();
    }
}
