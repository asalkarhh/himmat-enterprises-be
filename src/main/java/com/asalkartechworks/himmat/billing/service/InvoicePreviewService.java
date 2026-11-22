package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoicePreviewLineDto;
import com.asalkartechworks.himmat.billing.dto.InvoicePreviewRequest;
import com.asalkartechworks.himmat.billing.dto.InvoicePreviewResponseDto;
import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerSite;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.party.repository.CustomerSiteRepository;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineResponse;
import com.asalkartechworks.himmat.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePreviewService {

    private final CustomerRepository customerRepository;
    private final CustomerSiteRepository customerSiteRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PricingService pricingService;

    @Transactional(readOnly = true)
    public InvoicePreviewResponseDto previewInvoice(InvoicePreviewRequest request, boolean canViewProfit) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new ValidationException("Invoice preview must contain at least one line.");
        }

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", String.valueOf(request.getCustomerId())));

        if (!customer.isActive()) {
            throw new ValidationException("Cannot bill inactive customer: " + customer.getName());
        }

        CustomerSite site = null;
        if (request.getSiteId() != null) {
            site = customerSiteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerSite", String.valueOf(request.getSiteId())));

            if (!site.getCustomer().getId().equals(customer.getId())) {
                throw new ValidationException("Customer site with ID " + request.getSiteId() + " does not belong to customer " + customer.getId());
            }

            if (!site.isActive()) {
                throw new ValidationException("Cannot bill against inactive customer site: " + site.getSiteName());
            }
        }

        List<InvoicePreviewLineDto> previewLines = new ArrayList<>();
        List<String> overallWarnings = new ArrayList<>();
        StringBuilder fingerprintBuilder = new StringBuilder();

        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal totalPayableAmount = BigDecimal.ZERO;
        BigDecimal totalTaxableAmount = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        boolean allTaxAvailable = true;

        for (InvoiceLineRequest lineReq : request.getLines()) {
            ProductVariant variant = productVariantRepository.findByIdWithHierarchy(lineReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", String.valueOf(lineReq.getVariantId())));

            if (!variant.isActive() || (variant.getProduct() != null && !variant.getProduct().isActive())) {
                throw new ValidationException("Variant [" + variant.getSku() + "] or its product is inactive.");
            }

            PricingPreviewLineRequest pricingReq = PricingPreviewLineRequest.builder()
                    .variantId(variant.getId())
                    .quantity(lineReq.getQuantity())
                    .unitPriceOverride(lineReq.getUnitPriceOverride())
                    .discountPct(lineReq.getDiscountPct())
                    .overrideReason(lineReq.getOverrideReason())
                    .build();

            PricingPreviewLineResponse pResp = pricingService.priceLine(variant, pricingReq, canViewProfit);

            boolean isOverridden = lineReq.getUnitPriceOverride() != null;
            BigDecimal baseGrossLine = MoneyPolicy.roundMoney(pResp.getBaseInclusivePrice().multiply(BigDecimal.valueOf(lineReq.getQuantity())));

            grossTotal = grossTotal.add(baseGrossLine);
            totalPayableAmount = totalPayableAmount.add(pResp.getLineTotal());

            if (pResp.isTaxBreakdownAvailable() && pResp.getTaxableAmount() != null && pResp.getTaxAmount() != null) {
                totalTaxableAmount = totalTaxableAmount.add(pResp.getTaxableAmount());
                totalTaxAmount = totalTaxAmount.add(pResp.getTaxAmount());
            } else {
                allTaxAvailable = false;
            }

            InvoicePreviewLineDto lineDto = InvoicePreviewLineDto.builder()
                    .variantId(variant.getId())
                    .productName(variant.getProduct() != null ? variant.getProduct().getName() : "Unknown")
                    .sku(variant.getSku())
                    .sizeLabel(variant.getSizeLabel())
                    .pricingMethod(variant.getPricingMethod())
                    .quantity(lineReq.getQuantity())
                    .baseUnitPrice(pResp.getBaseInclusivePrice())
                    .discountPct(pResp.getDiscountPct())
                    .discountSource(pResp.getDiscountSource())
                    .unitNetPrice(pResp.getUnitNetInclusive())
                    .lineTotal(pResp.getLineTotal())
                    .isPriceOverridden(isOverridden)
                    .overrideReason(lineReq.getOverrideReason())
                    .gstRatePct(pResp.getGstRatePct())
                    .taxableAmount(pResp.getTaxableAmount())
                    .taxAmount(pResp.getTaxAmount())
                    .taxBreakdownAvailable(pResp.isTaxBreakdownAvailable())
                    .purchaseCostExclTax(variant.getPurchaseCostExclTax())
                    .costAvailable(pResp.isCostAvailable())
                    .warnings(pResp.getWarnings())
                    .build();

            previewLines.add(lineDto);

            // Accumulate for deterministic fingerprint
            fingerprintBuilder.append(variant.getId())
                    .append(":")
                    .append(variant.getVersion())
                    .append(":")
                    .append(pResp.getUnitNetInclusive().toPlainString())
                    .append(":")
                    .append(pResp.getLineTotal().toPlainString())
                    .append(";");
        }

        grossTotal = MoneyPolicy.roundMoney(grossTotal);
        totalPayableAmount = MoneyPolicy.roundMoney(totalPayableAmount);
        BigDecimal totalDiscountAmount = MoneyPolicy.roundMoney(grossTotal.subtract(totalPayableAmount));

        if (!allTaxAvailable) {
            overallWarnings.add("Tax breakdown is incomplete because one or more items lack a configured GST rate.");
            totalTaxableAmount = null;
            totalTaxAmount = null;
        } else {
            totalTaxableAmount = MoneyPolicy.roundMoney(totalTaxableAmount);
            totalTaxAmount = MoneyPolicy.roundMoney(totalTaxAmount);
        }

        boolean gstFormatCompletenessWarning = false;
        if (request.getDisplayFormat() == InvoiceDisplayFormat.GST && !allTaxAvailable) {
            gstFormatCompletenessWarning = true;
            overallWarnings.add("GST display format was requested, but tax breakdown is incomplete because one or more items lack a configured GST rate.");
        }

        String fingerprint = generateFingerprint(fingerprintBuilder.toString());

        return InvoicePreviewResponseDto.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .siteId(site != null ? site.getId() : null)
                .siteName(site != null ? site.getSiteName() : null)
                .displayFormat(request.getDisplayFormat() != null ? request.getDisplayFormat() : InvoiceDisplayFormat.NORMAL)
                .lines(previewLines)
                .grossTotal(grossTotal)
                .totalDiscountAmount(totalDiscountAmount)
                .totalTaxableAmount(totalTaxableAmount)
                .totalTaxAmount(totalTaxAmount)
                .totalPayableAmount(totalPayableAmount)
                .taxBreakdownAvailable(allTaxAvailable)
                .pricingFingerprint(fingerprint)
                .warnings(overallWarnings)
                .gstFormatCompletenessWarning(gstFormatCompletenessWarning)
                .build();
    }

    public static String generateFingerprint(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
