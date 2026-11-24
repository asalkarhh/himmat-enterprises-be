package com.asalkartechworks.himmat.reporting.service;

import com.asalkartechworks.himmat.billing.dto.DuesSummaryDto;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerRefund;
import com.asalkartechworks.himmat.billing.model.CustomerReturn;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevision;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerRefundRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReturnRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionRepository;
import com.asalkartechworks.himmat.billing.service.DuesService;
import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.model.OperationalExpense;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseRepository;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseReturn;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.model.SupplierRefund;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseReturnRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierRefundRepository;
import com.asalkartechworks.himmat.reporting.dto.DashboardSummaryDto;
import com.asalkartechworks.himmat.reporting.dto.ProfitReportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceRevisionRepository salesInvoiceRevisionRepository;
    private final CustomerReturnRepository customerReturnRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerRefundRepository customerRefundRepository;
    private final DuesService duesService;

    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierRefundRepository supplierRefundRepository;

    private final OperationalExpenseRepository operationalExpenseRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ProfitReportService profitReportService;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary(LocalDate startDate, LocalDate endDate, boolean isOwner) {
        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();

        // 1. Sales Metrics
        List<SalesInvoice> invoices = salesInvoiceRepository.findByStatusAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                InvoiceStatus.POSTED, effectiveStart, effectiveEnd);
        BigDecimal grossSales = BigDecimal.ZERO;
        for (SalesInvoice inv : invoices) {
            BigDecimal amt = inv.getOriginalPayableAmount() != null ? inv.getOriginalPayableAmount() : inv.getTotalPayableAmount();
            grossSales = grossSales.add(amt);
        }

        Instant startInstant = effectiveStart.atStartOfDay(BusinessClock.BUSINESS_ZONE).toInstant();
        Instant endInstant = effectiveEnd.plusDays(1).atStartOfDay(BusinessClock.BUSINESS_ZONE).toInstant();
        List<SalesInvoiceRevision> revisions = salesInvoiceRevisionRepository.findByCreatedAtBetween(startInstant, endInstant);
        BigDecimal revisionsDelta = BigDecimal.ZERO;
        for (SalesInvoiceRevision rev : revisions) {
            revisionsDelta = revisionsDelta.add(rev.getDeltaPayableAmount());
        }

        List<CustomerReturn> returns = customerReturnRepository.findByReturnDateBetweenOrderByReturnDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal returnsCredit = BigDecimal.ZERO;
        for (CustomerReturn ret : returns) {
            returnsCredit = returnsCredit.add(ret.getTotalReturnAmount());
        }

        BigDecimal netSales = grossSales.add(revisionsDelta).subtract(returnsCredit);

        // 2. Collections
        List<CustomerReceipt> receipts = customerReceiptRepository.findByReceiptDateBetweenOrderByReceiptDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal customerReceipts = BigDecimal.ZERO;
        for (CustomerReceipt r : receipts) {
            customerReceipts = customerReceipts.add(r.getTotalAmount());
        }

        List<CustomerRefund> refunds = customerRefundRepository.findByRefundDateBetweenOrderByRefundDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal customerRefunds = BigDecimal.ZERO;
        for (CustomerRefund cr : refunds) {
            customerRefunds = customerRefunds.add(cr.getTotalAmount());
        }

        BigDecimal netCollections = customerReceipts.subtract(customerRefunds);

        // 3. Customer Dues
        DuesSummaryDto duesSummary = duesService.getDuesSummary(null, null, null, null, false);
        List<SalesInvoice> allDueInvoices = salesInvoiceRepository.findAllDueInvoicesForSummary(
                null, null, null, null, false, businessClock.today());
        Set<Long> customersWithDues = new HashSet<>();
        for (SalesInvoice si : allDueInvoices) {
            if (si.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0) {
                customersWithDues.add(si.getCustomer().getId());
            }
        }

        // 4. Purchases & Payables
        List<Purchase> purchases = purchaseRepository.findByStatusAndPurchaseDateBetweenOrderByPurchaseDateAsc(
                PurchaseStatus.POSTED, effectiveStart, effectiveEnd);
        BigDecimal periodPurchases = BigDecimal.ZERO;
        for (Purchase p : purchases) {
            periodPurchases = periodPurchases.add(p.getTotalPayableAmount());
        }

        List<PurchaseReturn> purchaseReturns = purchaseReturnRepository.findByReturnDateBetweenOrderByReturnDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal periodPurchaseReturns = BigDecimal.ZERO;
        for (PurchaseReturn pr : purchaseReturns) {
            periodPurchaseReturns = periodPurchaseReturns.add(pr.getTotalReturnAmount());
        }

        BigDecimal netPurchases = periodPurchases.subtract(periodPurchaseReturns);

        List<SupplierPayment> payments = supplierPaymentRepository.findByPaymentDateBetweenOrderByPaymentDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal periodSupplierPayments = BigDecimal.ZERO;
        for (SupplierPayment sp : payments) {
            periodSupplierPayments = periodSupplierPayments.add(sp.getTotalAmount());
        }

        List<SupplierRefund> supplierRefunds = supplierRefundRepository.findByRefundDateBetweenOrderByRefundDateAsc(
                effectiveStart, effectiveEnd);
        BigDecimal periodSupplierRefunds = BigDecimal.ZERO;
        for (SupplierRefund sr : supplierRefunds) {
            periodSupplierRefunds = periodSupplierRefunds.add(sr.getTotalAmount());
        }

        // Outstanding supplier payables across all purchases
        List<Purchase> allUnpaidPurchases = purchaseRepository.findAll();
        BigDecimal totalSupplierOutstanding = BigDecimal.ZERO;
        Set<Long> suppliersWithPayables = new HashSet<>();
        for (Purchase p : allUnpaidPurchases) {
            if (p.getStatus() == PurchaseStatus.POSTED) {
                BigDecimal unpaid = p.getOutstandingAmount();
                if (unpaid.compareTo(BigDecimal.ZERO) > 0) {
                    totalSupplierOutstanding = totalSupplierOutstanding.add(unpaid);
                    suppliersWithPayables.add(p.getSupplier().getId());
                }
            }
        }

        // 5. Operating Expenses
        BigDecimal periodExpenses = operationalExpenseRepository.sumAmountByStatusAndDateBetween(
                ExpenseStatus.POSTED, effectiveStart, effectiveEnd);
        if (periodExpenses == null) {
            periodExpenses = BigDecimal.ZERO;
        }

        List<OperationalExpense> expenseList = operationalExpenseRepository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(
                ExpenseStatus.POSTED, effectiveStart, effectiveEnd);

        // 6. Inventory Health
        Page<InventoryBalance> balances = inventoryBalanceRepository.searchInventory(
                null, null, null, true, null, null, Pageable.unpaged());
        int zeroCount = 0;
        int lowCount = 0;
        for (InventoryBalance ib : balances) {
            int qty = ib.getQuantityOnHand() != null ? ib.getQuantityOnHand() : 0;
            ProductVariant v = ib.getVariant();
            int threshold = v.getLowStockThreshold() != null ? v.getLowStockThreshold() : 0;
            if (qty <= 0) {
                zeroCount++;
            } else if ((threshold > 0 && qty <= threshold) || (threshold == 0 && qty <= 5)) {
                lowCount++;
            }
        }

        // 7. Profit Summary (Owner only)
        DashboardSummaryDto.OwnerProfitSummaryDto profitSummary = null;
        if (isOwner) {
            ProfitReportDto p = profitReportService.getProfitReport(effectiveStart, effectiveEnd, null, null, null, null);
            profitSummary = DashboardSummaryDto.OwnerProfitSummaryDto.builder()
                    .netRevenue(p.getKnownNetRevenue())
                    .cogs(p.getKnownCogs())
                    .grossProfit(p.getKnownGrossProfit())
                    .grossMarginPct(p.getKnownGrossMarginPct())
                    .operatingExpenses(p.getTotalOperatingExpenses())
                    .netProfit(p.getEstimatedNetProfit())
                    .coveragePct(p.getCostCoveragePct())
                    .unknownCostSales(p.getSalesValueExcludedUnknownCost())
                    .build();
        }

        return DashboardSummaryDto.builder()
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .grossSales(MoneyPolicy.roundMoney(grossSales))
                .revisionsDelta(MoneyPolicy.roundMoney(revisionsDelta))
                .returnsCredit(MoneyPolicy.roundMoney(returnsCredit))
                .netSales(MoneyPolicy.roundMoney(netSales))
                .invoiceCount(invoices.size())
                .customerReceipts(MoneyPolicy.roundMoney(customerReceipts))
                .customerRefunds(MoneyPolicy.roundMoney(customerRefunds))
                .netCollections(MoneyPolicy.roundMoney(netCollections))
                .receiptCount(receipts.size())
                .totalCustomerOutstanding(duesSummary.getTotalOutstandingAmount())
                .overdueCustomerReceivables(duesSummary.getTotalOverdueAmount())
                .customersWithDuesCount(customersWithDues.size())
                .periodPurchases(MoneyPolicy.roundMoney(periodPurchases))
                .periodPurchaseReturns(MoneyPolicy.roundMoney(periodPurchaseReturns))
                .netPurchases(MoneyPolicy.roundMoney(netPurchases))
                .periodSupplierPayments(MoneyPolicy.roundMoney(periodSupplierPayments))
                .periodSupplierRefunds(MoneyPolicy.roundMoney(periodSupplierRefunds))
                .totalSupplierOutstanding(MoneyPolicy.roundMoney(totalSupplierOutstanding))
                .suppliersWithPayablesCount(suppliersWithPayables.size())
                .periodOperatingExpenses(MoneyPolicy.roundMoney(periodExpenses))
                .expenseCount(expenseList.size())
                .totalVariantsCount((int) balances.getTotalElements())
                .zeroStockVariantsCount(zeroCount)
                .lowStockVariantsCount(lowCount)
                .profitSummary(profitSummary)
                .build();
    }
}
