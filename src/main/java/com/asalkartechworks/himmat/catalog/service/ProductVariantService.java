package com.asalkartechworks.himmat.catalog.service;

import com.asalkartechworks.himmat.catalog.dto.CreateProductVariantRequest;
import com.asalkartechworks.himmat.catalog.dto.ProductVariantDto;
import com.asalkartechworks.himmat.catalog.dto.UpdateProductVariantRequest;
import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.errors.DuplicateResourceException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "sku", "sizeLabel", "pricingMethod", "mrpInclTax", "fixedPriceInclTax",
            "purchaseCostExclTax", "isActive", "createdAt", "updatedAt"
    );

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public Page<ProductVariantDto> getVariants(Long productId, String search, Boolean isActive, Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return productVariantRepository.searchVariants(productId, search, isActive, sanitized).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductVariantDto getVariantById(Long id) {
        ProductVariant variant = productVariantRepository.findByIdWithHierarchy(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", String.valueOf(id)));
        return toDto(variant);
    }

    @Transactional
    public ProductVariantDto createVariant(CreateProductVariantRequest request, Long currentActorId) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(request.getProductId())));

        if (!product.isActive()) {
            throw new ValidationException("Cannot add variant to inactive product: " + product.getName());
        }

        String trimmedSku = request.getSku().trim();
        if (productVariantRepository.existsBySku(trimmedSku)) {
            throw new DuplicateResourceException("Product variant with SKU '" + trimmedSku + "' already exists.");
        }

        String trimmedSize = request.getSizeLabel().trim();
        if (productVariantRepository.existsByProductIdAndSizeLabelIgnoreCase(product.getId(), trimmedSize)) {
            throw new DuplicateResourceException(
                    "Size label '" + trimmedSize + "' already exists for product '" + product.getName() + "'."
            );
        }

        validatePricingMethodInputs(request.getPricingMethod(), request.getMrpInclTax(), request.getFixedPriceInclTax(),
                request.getPurchaseCostExclTax(), request.getProfitPct(), request.getGstRatePct());

        Instant now = businessClock.now();

        // 1. Atomically create product_variant
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(trimmedSku)
                .sizeLabel(trimmedSize)
                .pricingMethod(request.getPricingMethod())
                .mrpInclTax(request.getMrpInclTax() != null ? MoneyPolicy.roundMoney(request.getMrpInclTax()) : null)
                .fixedPriceInclTax(request.getFixedPriceInclTax() != null ? MoneyPolicy.roundMoney(request.getFixedPriceInclTax()) : null)
                .purchaseCostExclTax(request.getPurchaseCostExclTax() != null ? MoneyPolicy.roundUnitRate(request.getPurchaseCostExclTax()) : null)
                .profitPct(request.getProfitPct() != null ? MoneyPolicy.roundPercent(request.getProfitPct()) : null)
                .gstRatePct(request.getGstRatePct() != null ? MoneyPolicy.roundPercent(request.getGstRatePct()) : null)
                .hsnCode(request.getHsnCode() != null ? request.getHsnCode().trim() : null)
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 0)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActorId)
                .updatedBy(currentActorId)
                .build();

        ProductVariant savedVariant = productVariantRepository.save(variant);

        // 2. Atomically create inventory_balance record (quantity_on_hand=0, opening_confirmed=false)
        InventoryBalance initialBalance = InventoryBalance.builder()
                .variantId(savedVariant.getId())
                .variant(savedVariant)
                .quantityOnHand(0)
                .openingConfirmed(false)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .updatedBy(currentActorId)
                .build();

        inventoryBalanceRepository.save(initialBalance);
        log.info("Atomically created variant ID: {} (SKU: {}) and initial inventory balance (quantity: 0)",
                savedVariant.getId(), savedVariant.getSku());

        return toDto(savedVariant);
    }

    @Transactional
    public ProductVariantDto updateVariant(Long id, UpdateProductVariantRequest request, Long currentActorId) {
        ProductVariant variant = productVariantRepository.findByIdWithHierarchy(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", String.valueOf(id)));

        if (!variant.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException("The record changed. Reload before editing.", variant.getVersion());
        }

        if (request.getSku() != null && !request.getSku().isBlank()) {
            String trimmedSku = request.getSku().trim();
            if (productVariantRepository.existsBySkuAndIdNot(trimmedSku, id)) {
                throw new DuplicateResourceException("Product variant with SKU '" + trimmedSku + "' already exists.");
            }
            variant.setSku(trimmedSku);
        }

        if (request.getSizeLabel() != null && !request.getSizeLabel().isBlank()) {
            String trimmedSize = request.getSizeLabel().trim();
            if (productVariantRepository.existsByProductIdAndSizeLabelIgnoreCaseAndIdNot(variant.getProduct().getId(), trimmedSize, id)) {
                throw new DuplicateResourceException(
                        "Size label '" + trimmedSize + "' already exists for product '" + variant.getProduct().getName() + "'."
                );
            }
            variant.setSizeLabel(trimmedSize);
        }

        if (request.getPricingMethod() != null) {
            variant.setPricingMethod(request.getPricingMethod());
        }

        if (request.getMrpInclTax() != null) {
            variant.setMrpInclTax(MoneyPolicy.roundMoney(request.getMrpInclTax()));
        }

        if (request.getFixedPriceInclTax() != null) {
            variant.setFixedPriceInclTax(MoneyPolicy.roundMoney(request.getFixedPriceInclTax()));
        }

        if (request.getPurchaseCostExclTax() != null) {
            variant.setPurchaseCostExclTax(MoneyPolicy.roundUnitRate(request.getPurchaseCostExclTax()));
        }

        if (request.getProfitPct() != null) {
            variant.setProfitPct(MoneyPolicy.roundPercent(request.getProfitPct()));
        }

        if (request.getGstRatePct() != null) {
            variant.setGstRatePct(MoneyPolicy.roundPercent(request.getGstRatePct()));
        }

        if (request.getHsnCode() != null) {
            variant.setHsnCode(request.getHsnCode().trim());
        }

        if (request.getLowStockThreshold() != null) {
            variant.setLowStockThreshold(request.getLowStockThreshold());
        }

        if (request.getIsActive() != null) {
            variant.setActive(request.getIsActive());
        }

        variant.setUpdatedAt(businessClock.now());
        variant.setUpdatedBy(currentActorId);

        ProductVariant saved = productVariantRepository.save(variant);
        log.info("Updated variant ID: {} version: {}", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    public ProductVariantDto toDto(ProductVariant variant) {
        Product prod = variant.getProduct();
        Subcategory sub = prod != null ? prod.getSubcategory() : null;
        var cat = sub != null ? sub.getCategory() : null;

        Optional<InventoryBalance> balanceOpt = inventoryBalanceRepository.findByVariantId(variant.getId());
        int qty = balanceOpt.map(InventoryBalance::getQuantityOnHand).orElse(0);
        boolean opening = balanceOpt.map(InventoryBalance::isOpeningConfirmed).orElse(false);

        return ProductVariantDto.builder()
                .id(variant.getId())
                .productId(prod != null ? prod.getId() : null)
                .productName(prod != null ? prod.getName() : null)
                .brandId(prod != null && prod.getBrand() != null ? prod.getBrand().getId() : null)
                .brandName(prod != null && prod.getBrand() != null ? prod.getBrand().getName() : null)
                .categoryId(cat != null ? cat.getId() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .subcategoryId(sub != null ? sub.getId() : null)
                .subcategoryName(sub != null ? sub.getName() : null)
                .sku(variant.getSku())
                .sizeLabel(variant.getSizeLabel())
                .pricingMethod(variant.getPricingMethod())
                .mrpInclTax(variant.getMrpInclTax())
                .fixedPriceInclTax(variant.getFixedPriceInclTax())
                .purchaseCostExclTax(variant.getPurchaseCostExclTax())
                .profitPct(variant.getProfitPct())
                .gstRatePct(variant.getGstRatePct())
                .hsnCode(variant.getHsnCode())
                .lowStockThreshold(variant.getLowStockThreshold())
                .quantityOnHand(qty)
                .openingConfirmed(opening)
                .isActive(variant.isActive())
                .version(variant.getVersion())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }

    private void validatePricingMethodInputs(PricingMethod method, java.math.BigDecimal mrp,
                                            java.math.BigDecimal fixed, java.math.BigDecimal cost,
                                            java.math.BigDecimal profit, java.math.BigDecimal gst) {
        if (method == PricingMethod.MRP_DISCOUNT && mrp != null && mrp.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new ValidationException("MRP inclusive of tax cannot be negative.");
        }
        if (method == PricingMethod.FIXED && fixed != null && fixed.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new ValidationException("Fixed price inclusive of tax cannot be negative.");
        }
        if (method == PricingMethod.COST_PLUS && cost != null && cost.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new ValidationException("Purchase cost cannot be negative.");
        }
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    sort = Sort.by(order.getDirection(), order.getProperty()).and(Sort.by(Sort.Direction.ASC, "id"));
                    break;
                }
            }
        }
        return PageRequest.of(page, size, sort);
    }
}
