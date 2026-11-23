package com.asalkartechworks.himmat.reporting.service;

import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseRepository;
import com.asalkartechworks.himmat.reporting.dto.ProfitReportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitReportService {

    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final OperationalExpenseRepository operationalExpenseRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public ProfitReportDto getProfitReport(
            LocalDate startDate,
            LocalDate endDate,
            Long variantId,
            Long productId,
            Long brandId,
            Long categoryId) {

        LocalDate effectiveStart = startDate != null ? startDate : businessClock.today().withDayOfMonth(1);
        LocalDate effectiveEnd = endDate != null ? endDate : businessClock.today();

        List<SalesInvoiceLine> lines = salesInvoiceLineRepository.findLinesForProfitReport(effectiveStart, effectiveEnd);

        BigDecimal knownNetRevenue = BigDecimal.ZERO;
        BigDecimal knownCogs = BigDecimal.ZERO;
        BigDecimal knownGrossProfit = BigDecimal.ZERO;
        BigDecimal salesValueExcludedUnknownCost = BigDecimal.ZERO;

        Map<Long, ProductProfitAccumulator> accumulatorMap = new HashMap<>();

        for (SalesInvoiceLine line : lines) {
            ProductVariant variant = line.getVariant();
            Product product = variant.getProduct();

            // Filter criteria
            if (variantId != null && !variant.getId().equals(variantId)) continue;
            if (productId != null && !product.getId().equals(productId)) continue;
            if (brandId != null && product.getBrand() != null && !product.getBrand().getId().equals(brandId)) continue;
            if (categoryId != null && product.getSubcategory() != null && product.getSubcategory().getCategory() != null
                    && !product.getSubcategory().getCategory().getId().equals(categoryId)) continue;

            int originalQty = line.getQuantity() != null ? line.getQuantity() : 0;
            int returnedQty = line.getReturnedQuantity() != null ? line.getReturnedQuantity() : 0;
            boolean isActive = line.isActiveInCurrentRevision();

            int effectiveQty = isActive ? Math.max(0, originalQty - returnedQty) : 0;
            if (effectiveQty <= 0 && returnedQty == 0) {
                continue;
            }

            ProductProfitAccumulator acc = accumulatorMap.computeIfAbsent(variant.getId(),
                    id -> new ProductProfitAccumulator(variant));

            acc.quantitySold += originalQty;
            acc.quantityReturned += returnedQty;
            acc.netQuantity += effectiveQty;

            if (line.isCostAvailable() && line.getPurchaseCostExclTax() != null) {
                acc.costAvailable = true;

                // Revenue calculation for effective remaining sold units
                BigDecimal lineRevenue;
                if (line.isTaxBreakdownAvailable() && line.getTaxableAmount() != null && originalQty > 0) {
                    BigDecimal unitTaxable = line.getTaxableAmount().divide(BigDecimal.valueOf(originalQty), 4, RoundingMode.HALF_UP);
                    lineRevenue = unitTaxable.multiply(BigDecimal.valueOf(effectiveQty));
                } else if (line.getUnitNetPrice() != null) {
                    lineRevenue = line.getUnitNetPrice().multiply(BigDecimal.valueOf(effectiveQty));
                } else if (originalQty > 0) {
                    BigDecimal unitPrice = line.getLineTotal().divide(BigDecimal.valueOf(originalQty), 4, RoundingMode.HALF_UP);
                    lineRevenue = unitPrice.multiply(BigDecimal.valueOf(effectiveQty));
                } else {
                    lineRevenue = BigDecimal.ZERO;
                }

                BigDecimal lineCogs = line.getPurchaseCostExclTax().multiply(BigDecimal.valueOf(effectiveQty));
                BigDecimal lineProfit = lineRevenue.subtract(lineCogs);

                acc.netRevenue = acc.netRevenue.add(lineRevenue);
                acc.netCogs = acc.netCogs.add(lineCogs);
                acc.grossProfit = acc.grossProfit.add(lineProfit);

                knownNetRevenue = knownNetRevenue.add(lineRevenue);
                knownCogs = knownCogs.add(lineCogs);
                knownGrossProfit = knownGrossProfit.add(lineProfit);
            } else {
                // Cost is unknown - exclude from known gross profit to protect owner truth
                BigDecimal unknownSalesVal;
                if (originalQty > 0) {
                    BigDecimal unitPrice = (line.getTaxableAmount() != null ? line.getTaxableAmount() : line.getLineTotal())
                            .divide(BigDecimal.valueOf(originalQty), 4, RoundingMode.HALF_UP);
                    unknownSalesVal = unitPrice.multiply(BigDecimal.valueOf(effectiveQty));
                } else {
                    unknownSalesVal = BigDecimal.ZERO;
                }

                acc.netRevenue = acc.netRevenue.add(unknownSalesVal);
                salesValueExcludedUnknownCost = salesValueExcludedUnknownCost.add(unknownSalesVal);
            }
        }

        BigDecimal totalNetSales = knownNetRevenue.add(salesValueExcludedUnknownCost);

        BigDecimal knownGrossMarginPct = BigDecimal.ZERO;
        if (knownNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            knownGrossMarginPct = knownGrossProfit.multiply(BigDecimal.valueOf(100))
                    .divide(knownNetRevenue, 2, RoundingMode.HALF_UP);
        }

        BigDecimal costCoveragePct = BigDecimal.valueOf(100.00);
        if (totalNetSales.compareTo(BigDecimal.ZERO) > 0) {
            costCoveragePct = knownNetRevenue.multiply(BigDecimal.valueOf(100))
                    .divide(totalNetSales, 2, RoundingMode.HALF_UP);
        }

        BigDecimal periodExpenses = operationalExpenseRepository.sumAmountByStatusAndDateBetween(
                ExpenseStatus.POSTED, effectiveStart, effectiveEnd);
        if (periodExpenses == null) {
            periodExpenses = BigDecimal.ZERO;
        }

        BigDecimal estimatedNetProfit = knownGrossProfit.subtract(periodExpenses);

        List<ProfitReportDto.ProductProfitItemDto> productItems = new ArrayList<>();
        for (ProductProfitAccumulator acc : accumulatorMap.values()) {
            BigDecimal marginPct = BigDecimal.ZERO;
            if (acc.costAvailable && acc.netRevenue.compareTo(BigDecimal.ZERO) > 0) {
                marginPct = acc.grossProfit.multiply(BigDecimal.valueOf(100))
                        .divide(acc.netRevenue, 2, RoundingMode.HALF_UP);
            }

            productItems.add(ProfitReportDto.ProductProfitItemDto.builder()
                    .variantId(acc.variant.getId())
                    .productId(acc.variant.getProduct().getId())
                    .sku(acc.variant.getSku())
                    .productName(acc.variant.getProduct().getName())
                    .sizeLabel(acc.variant.getSizeLabel())
                    .brandName(acc.variant.getProduct().getBrand() != null ? acc.variant.getProduct().getBrand().getName() : "")
                    .categoryName(acc.variant.getProduct().getSubcategory() != null && acc.variant.getProduct().getSubcategory().getCategory() != null
                            ? acc.variant.getProduct().getSubcategory().getCategory().getName() : "")
                    .quantitySold(acc.quantitySold)
                    .quantityReturned(acc.quantityReturned)
                    .netQuantity(acc.netQuantity)
                    .netRevenue(MoneyPolicy.roundMoney(acc.netRevenue))
                    .netCogs(MoneyPolicy.roundMoney(acc.netCogs))
                    .grossProfit(MoneyPolicy.roundMoney(acc.grossProfit))
                    .grossMarginPct(marginPct)
                    .costAvailable(acc.costAvailable)
                    .build());
        }

        return ProfitReportDto.builder()
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .knownNetRevenue(MoneyPolicy.roundMoney(knownNetRevenue))
                .knownCogs(MoneyPolicy.roundMoney(knownCogs))
                .knownGrossProfit(MoneyPolicy.roundMoney(knownGrossProfit))
                .knownGrossMarginPct(knownGrossMarginPct)
                .salesValueExcludedUnknownCost(MoneyPolicy.roundMoney(salesValueExcludedUnknownCost))
                .totalNetSales(MoneyPolicy.roundMoney(totalNetSales))
                .costCoveragePct(costCoveragePct)
                .totalOperatingExpenses(MoneyPolicy.roundMoney(periodExpenses))
                .estimatedNetProfit(MoneyPolicy.roundMoney(estimatedNetProfit))
                .productBreakdown(productItems)
                .build();
    }

    private static class ProductProfitAccumulator {
        private final ProductVariant variant;
        private int quantitySold = 0;
        private int quantityReturned = 0;
        private int netQuantity = 0;
        private BigDecimal netRevenue = BigDecimal.ZERO;
        private BigDecimal netCogs = BigDecimal.ZERO;
        private BigDecimal grossProfit = BigDecimal.ZERO;
        private boolean costAvailable = false;

        public ProductProfitAccumulator(ProductVariant variant) {
            this.variant = variant;
        }
    }
}
