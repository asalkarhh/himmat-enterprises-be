package com.asalkartechworks.himmat.pricing.service;

import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineResponse;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    public PricingPreviewResponse calculatePreview(PricingPreviewRequest request, boolean canViewProfit) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new ValidationException("At least one preview line is required.");
        }

        List<PricingPreviewLineResponse> lineResponses = new ArrayList<>();
        List<String> overallWarnings = new ArrayList<>();

        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal totalTaxableAmount = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        boolean allTaxAvailable = true;

        for (PricingPreviewLineRequest lineReq : request.getLines()) {
            ProductVariant variant = productVariantRepository.findByIdWithHierarchy(lineReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", String.valueOf(lineReq.getVariantId())));

            if (!variant.isActive()) {
                throw new ValidationException("Cannot price inactive variant: " + variant.getSku());
            }

            PricingPreviewLineResponse lineResp = priceLine(variant, lineReq, canViewProfit);
            lineResponses.add(lineResp);

            grossTotal = grossTotal.add(lineResp.getLineTotal());

            if (lineResp.isTaxBreakdownAvailable() && lineResp.getTaxableAmount() != null && lineResp.getTaxAmount() != null) {
                totalTaxableAmount = totalTaxableAmount.add(lineResp.getTaxableAmount());
                totalTaxAmount = totalTaxAmount.add(lineResp.getTaxAmount());
            } else {
                allTaxAvailable = false;
            }
        }

        grossTotal = MoneyPolicy.roundMoney(grossTotal);

        if (!allTaxAvailable) {
            overallWarnings.add("Tax breakdown is incomplete because one or more items have unconfigured GST rates.");
            totalTaxableAmount = null;
            totalTaxAmount = null;
        } else {
            totalTaxableAmount = MoneyPolicy.roundMoney(totalTaxableAmount);
            totalTaxAmount = MoneyPolicy.roundMoney(totalTaxAmount);
        }

        return PricingPreviewResponse.builder()
                .lines(lineResponses)
                .grossTotal(grossTotal)
                .totalTaxableAmount(totalTaxableAmount)
                .totalTaxAmount(totalTaxAmount)
                .taxBreakdownAvailable(allTaxAvailable)
                .warnings(overallWarnings)
                .build();
    }

    public PricingPreviewLineResponse priceLine(ProductVariant variant,
                                                PricingPreviewLineRequest lineReq,
                                                boolean canViewProfit) {
        int qty = lineReq.getQuantity();
        if (qty <= 0) {
            throw new ValidationException("Quantity must be a positive whole integer piece.");
        }

        Product product = variant.getProduct();
        Category category = product.getSubcategory() != null ? product.getSubcategory().getCategory() : null;

        PricingMethod method = variant.getPricingMethod();
        BigDecimal baseInclusivePrice;
        BigDecimal discountPct = BigDecimal.ZERO;
        String discountSource = "NONE";
        List<String> warnings = new ArrayList<>();

        switch (method) {
            case MRP_DISCOUNT -> {
                if (lineReq.getUnitPriceOverride() != null) {
                    baseInclusivePrice = MoneyPolicy.roundUnitRate(lineReq.getUnitPriceOverride());
                } else {
                    if (variant.getMrpInclTax() == null) {
                        throw new ValidationException("MRP inclusive of tax is not configured for variant: " + variant.getSku());
                    }
                    baseInclusivePrice = MoneyPolicy.roundUnitRate(variant.getMrpInclTax());
                }

                if (lineReq.getDiscountPct() != null) {
                    discountPct = MoneyPolicy.roundPercent(lineReq.getDiscountPct());
                    discountSource = "EXPLICIT_LINE_OVERRIDE";
                } else if (category != null && category.getSalesDiscountPct() != null && category.getSalesDiscountPct().compareTo(BigDecimal.ZERO) > 0) {
                    discountPct = MoneyPolicy.roundPercent(category.getSalesDiscountPct());
                    discountSource = "CATEGORY_SALES_DISCOUNT";
                } else {
                    discountPct = BigDecimal.ZERO.setScale(MoneyPolicy.PERCENT_SCALE, MoneyPolicy.DEFAULT_ROUNDING);
                    discountSource = "NONE";
                }
            }
            case COST_PLUS -> {
                if (lineReq.getUnitPriceOverride() != null) {
                    baseInclusivePrice = MoneyPolicy.roundUnitRate(lineReq.getUnitPriceOverride());
                } else {
                    if (variant.getPurchaseCostExclTax() == null || variant.getProfitPct() == null || variant.getGstRatePct() == null) {
                        throw new ValidationException(
                                "COST_PLUS pricing requires configured purchase cost, profit percentage, and GST rate for variant: " + variant.getSku()
                        );
                    }
                    // baseInclusivePrice = purchaseCostExclTax * (1 + profitPct / 100) * (1 + gstRatePct / 100)
                    BigDecimal cost = variant.getPurchaseCostExclTax();
                    BigDecimal profitMultiplier = BigDecimal.ONE.add(variant.getProfitPct().divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
                    BigDecimal gstMultiplier = BigDecimal.ONE.add(variant.getGstRatePct().divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
                    baseInclusivePrice = MoneyPolicy.roundUnitRate(cost.multiply(profitMultiplier).multiply(gstMultiplier));
                }

                // Rule: Do NOT apply category sales discount automatically to COST_PLUS!
                if (lineReq.getDiscountPct() != null) {
                    discountPct = MoneyPolicy.roundPercent(lineReq.getDiscountPct());
                    discountSource = "EXPLICIT_LINE_OVERRIDE";
                } else {
                    discountPct = BigDecimal.ZERO.setScale(MoneyPolicy.PERCENT_SCALE, MoneyPolicy.DEFAULT_ROUNDING);
                    discountSource = "NONE";
                }
            }
            case FIXED -> {
                if (lineReq.getUnitPriceOverride() != null) {
                    baseInclusivePrice = MoneyPolicy.roundUnitRate(lineReq.getUnitPriceOverride());
                } else {
                    if (variant.getFixedPriceInclTax() == null) {
                        throw new ValidationException("Fixed price inclusive of tax is not configured for variant: " + variant.getSku());
                    }
                    baseInclusivePrice = MoneyPolicy.roundUnitRate(variant.getFixedPriceInclTax());
                }

                if (lineReq.getDiscountPct() != null && lineReq.getDiscountPct().compareTo(BigDecimal.ZERO) > 0) {
                    throw new ValidationException("Discounts are prohibited on FIXED pricing method for variant: " + variant.getSku());
                }
                discountPct = BigDecimal.ZERO.setScale(MoneyPolicy.PERCENT_SCALE, MoneyPolicy.DEFAULT_ROUNDING);
                discountSource = "NONE";
            }
            default -> throw new ValidationException("Unsupported pricing method: " + method);
        }

        // Calculate authoritative net unit price: baseInclusivePrice * (1 - discountPct / 100)
        BigDecimal discountFactor = BigDecimal.ONE.subtract(discountPct.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal unitNetInclusive = MoneyPolicy.roundUnitRate(baseInclusivePrice.multiply(discountFactor));

        // Line total: unitNetInclusive * quantity (rounded to 2 decimals HALF_UP)
        BigDecimal lineTotal = MoneyPolicy.roundMoney(unitNetInclusive.multiply(BigDecimal.valueOf(qty)));

        // GST breakdown
        BigDecimal taxableAmount = null;
        BigDecimal taxAmount = null;
        boolean taxBreakdownAvailable = false;

        if (variant.getGstRatePct() != null) {
            BigDecimal gstRate = variant.getGstRatePct();
            BigDecimal taxMultiplier = BigDecimal.ONE.add(gstRate.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
            taxableAmount = MoneyPolicy.roundMoney(lineTotal.divide(taxMultiplier, 2, RoundingMode.HALF_UP));
            taxAmount = MoneyPolicy.roundMoney(lineTotal.subtract(taxableAmount));
            taxBreakdownAvailable = true;
        } else {
            warnings.add("GST rate is unconfigured. Tax breakdown is unavailable.");
        }

        boolean costAvailable = (variant.getPurchaseCostExclTax() != null);
        BigDecimal profitAmount = null;

        if (canViewProfit && costAvailable && taxableAmount != null) {
            BigDecimal totalCost = MoneyPolicy.roundMoney(variant.getPurchaseCostExclTax().multiply(BigDecimal.valueOf(qty)));
            profitAmount = MoneyPolicy.roundMoney(taxableAmount.subtract(totalCost));
        }

        return PricingPreviewLineResponse.builder()
                .variantId(variant.getId())
                .sku(variant.getSku())
                .productName(product.getName())
                .sizeLabel(variant.getSizeLabel())
                .pricingMethod(method)
                .quantity(qty)
                .baseInclusivePrice(baseInclusivePrice)
                .discountPct(discountPct)
                .discountSource(discountSource)
                .unitNetInclusive(unitNetInclusive)
                .lineTotal(lineTotal)
                .gstRatePct(variant.getGstRatePct())
                .taxableAmount(taxableAmount)
                .taxAmount(taxAmount)
                .taxBreakdownAvailable(taxBreakdownAvailable)
                .costAvailable(costAvailable)
                .profitAmount(profitAmount)
                .variantVersion(variant.getVersion())
                .categoryVersion(category != null ? category.getVersion() : null)
                .warnings(warnings)
                .build();
    }
}
