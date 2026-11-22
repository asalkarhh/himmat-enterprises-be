package com.asalkartechworks.himmat.inventory.service;

import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.dto.InventoryItemDto;
import com.asalkartechworks.himmat.inventory.dto.StockMovementDto;
import com.asalkartechworks.himmat.inventory.dto.StockStatusFilter;
import com.asalkartechworks.himmat.inventory.model.StockMovement;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private static final Set<String> ALLOWED_INVENTORY_SORT_FIELDS = Set.of(
            "variantId", "quantityOnHand", "updatedAt", "createdAt"
    );

    private static final Set<String> ALLOWED_MOVEMENT_SORT_FIELDS = Set.of(
            "id", "occurredAt", "createdAt", "quantity"
    );

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public Page<InventoryItemDto> getInventory(Long brandId,
                                              Long categoryId,
                                              Long subcategoryId,
                                              Boolean isActive,
                                              String search,
                                              StockStatusFilter stockStatus,
                                              Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable, ALLOWED_INVENTORY_SORT_FIELDS, "variantId");
        String statusStr = (stockStatus != null && stockStatus != StockStatusFilter.ALL) ? stockStatus.name() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        return inventoryBalanceRepository.searchInventory(brandId, categoryId, subcategoryId, isActive, cleanSearch, statusStr, sanitized)
                .map(this::toInventoryItemDto);
    }

    @Transactional(readOnly = true)
    public InventoryItemDto getInventoryByVariantId(Long variantId) {
        InventoryBalance ib = inventoryBalanceRepository.findByVariantIdWithHierarchy(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryBalance", String.valueOf(variantId)));
        return toInventoryItemDto(ib);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovements(Long variantId,
                                              StockMovementType movementType,
                                              StockMovementDirection direction,
                                              String sourceEntityType,
                                              Long businessEventId,
                                              LocalDate fromDate,
                                              LocalDate toDate,
                                              Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable, ALLOWED_MOVEMENT_SORT_FIELDS, "id");

        Instant startInstant = fromDate != null ? fromDate.atStartOfDay(BusinessClock.BUSINESS_ZONE).toInstant() : null;
        Instant endInstant = toDate != null ? toDate.plusDays(1).atStartOfDay(BusinessClock.BUSINESS_ZONE).toInstant() : null;

        return stockMovementRepository.searchMovements(variantId, movementType, direction, sourceEntityType, businessEventId, startInstant, endInstant, sanitized)
                .map(this::toStockMovementDto);
    }

    public InventoryItemDto toInventoryItemDto(InventoryBalance ib) {
        ProductVariant v = ib.getVariant();
        Product p = v.getProduct();
        Subcategory sub = p != null ? p.getSubcategory() : null;
        Category cat = sub != null ? sub.getCategory() : null;
        Brand brand = p != null ? p.getBrand() : null;

        int qty = ib.getQuantityOnHand();
        int threshold = v.getLowStockThreshold() != null ? v.getLowStockThreshold() : 0;
        boolean lowStock = threshold > 0 && qty <= threshold;

        return InventoryItemDto.builder()
                .variantId(ib.getVariantId())
                .productId(p != null ? p.getId() : null)
                .productName(p != null ? p.getName() : null)
                .brandId(brand != null ? brand.getId() : null)
                .brandName(brand != null ? brand.getName() : null)
                .categoryId(cat != null ? cat.getId() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .subcategoryId(sub != null ? sub.getId() : null)
                .subcategoryName(sub != null ? sub.getName() : null)
                .sku(v.getSku())
                .sizeLabel(v.getSizeLabel())
                .pricingMethod(v.getPricingMethod())
                .mrpInclTax(v.getMrpInclTax())
                .fixedPriceInclTax(v.getFixedPriceInclTax())
                .quantityOnHand(qty)
                .lowStockThreshold(threshold)
                .isLowStock(lowStock)
                .openingConfirmed(ib.isOpeningConfirmed())
                .version(ib.getVersion())
                .isActive(v.isActive() && (p == null || p.isActive()))
                .updatedAt(ib.getUpdatedAt())
                .build();
    }

    public StockMovementDto toStockMovementDto(StockMovement sm) {
        ProductVariant v = sm.getVariant();
        Product p = v != null ? v.getProduct() : null;

        return StockMovementDto.builder()
                .id(sm.getId())
                .variantId(v != null ? v.getId() : null)
                .sku(v != null ? v.getSku() : null)
                .sizeLabel(v != null ? v.getSizeLabel() : null)
                .productId(p != null ? p.getId() : null)
                .productName(p != null ? p.getName() : null)
                .businessEventId(sm.getBusinessEvent() != null ? sm.getBusinessEvent().getId() : null)
                .movementType(sm.getMovementType())
                .direction(sm.getDirection())
                .quantity(sm.getQuantity())
                .quantityBefore(sm.getQuantityBefore())
                .quantityAfter(sm.getQuantityAfter())
                .sourceEntityType(sm.getSourceEntityType())
                .sourceEntityId(sm.getSourceEntityId())
                .sourceLineId(sm.getSourceLineId())
                .sourceEffectKey(sm.getSourceEffectKey())
                .reason(sm.getReason())
                .actorId(sm.getActorId())
                .occurredAt(sm.getOccurredAt())
                .createdAt(sm.getCreatedAt())
                .build();
    }

    private Pageable sanitizePageable(Pageable pageable, Set<String> allowedFields, String defaultSortField) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort sort = Sort.by(Sort.Direction.DESC, defaultSortField);
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                if (allowedFields.contains(order.getProperty())) {
                    sort = Sort.by(order.getDirection(), order.getProperty());
                    break;
                }
            }
        }
        return PageRequest.of(page, size, sort);
    }
}
