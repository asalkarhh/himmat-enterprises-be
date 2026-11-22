package com.asalkartechworks.himmat.inventory.service;

import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.DuplicateResourceException;
import com.asalkartechworks.himmat.common.errors.InactiveVariantException;
import com.asalkartechworks.himmat.common.errors.InsufficientStockException;
import com.asalkartechworks.himmat.common.errors.InventoryBalanceMissingException;
import com.asalkartechworks.himmat.common.errors.InvalidQuantityException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.model.StockMovement;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.repository.StockMovementRepository;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reusable transactional stock posting service.
 * Enforces deadlock-free row locking (ascending variant ID order), running balance arithmetic,
 * strict non-negative stock invariants, and appends immutable StockMovement audit records.
 *
 * MUST participate in an existing caller transaction (Propagation.MANDATORY).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPostingService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BusinessClock businessClock;

    @Transactional(propagation = Propagation.MANDATORY)
    public StockPostingResult postMovements(StockPostingRequest request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "Posting request must contain at least one line.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (request.getBusinessEventId() == null) {
            throw new AppException("VALIDATION_FAILED", "businessEventId is required for stock posting.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 1. Line-level validation
        for (StockPostingLine line : request.getLines()) {
            if (line.getVariantId() == null) {
                throw new AppException("VALIDATION_FAILED", "variantId is required on every line.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new InvalidQuantityException("Movement quantity must be a positive integer greater than zero.");
            }
            if (line.getDirection() == null) {
                throw new AppException("VALIDATION_FAILED", "direction is required on every line.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (line.getMovementType() == null) {
                throw new AppException("VALIDATION_FAILED", "movementType is required on every line.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (line.getSourceEffectKey() != null && stockMovementRepository.existsBySourceEffectKey(line.getSourceEffectKey())) {
                throw new DuplicateResourceException("Source effect key '" + line.getSourceEffectKey() + "' was already processed.");
            }
        }

        // 2. Deadlock-free row locking: sort distinct variant IDs in ascending numerical order
        List<Long> sortedVariantIds = request.getLines().stream()
                .map(StockPostingLine::getVariantId)
                .distinct()
                .sorted()
                .toList();

        List<InventoryBalance> lockedBalances = inventoryBalanceRepository.findAllByVariantIdInOrderByIdAscWithLock(sortedVariantIds);
        Map<Long, InventoryBalance> balanceMap = lockedBalances.stream()
                .collect(Collectors.toMap(InventoryBalance::getVariantId, Function.identity()));

        // 3. Verify all requested variant balances exist in database
        for (Long variantId : sortedVariantIds) {
            if (!balanceMap.containsKey(variantId)) {
                log.error("Integrity error: Inventory balance record missing for variant ID: {}", variantId);
                throw new InventoryBalanceMissingException(variantId);
            }
        }

        // 4. Validate active status if not allowing historical inactive variants
        if (!request.isAllowInactiveVariants()) {
            for (InventoryBalance ib : lockedBalances) {
                ProductVariant variant = ib.getVariant();
                if (!variant.isActive() || (variant.getProduct() != null && !variant.getProduct().isActive())) {
                    throw new InactiveVariantException("Variant [" + variant.getSku() + "] or its product is inactive.");
                }
            }
        }

        Instant now = businessClock.now();
        Instant movementOccurredAt = request.getOccurredAt() != null ? request.getOccurredAt() : now;
        BusinessEvent eventRef = BusinessEvent.builder().id(request.getBusinessEventId()).build();

        List<PostedLineResult> lineResults = new ArrayList<>();

        // 5. Apply each line sequentially against running balance
        for (StockPostingLine line : request.getLines()) {
            InventoryBalance ib = balanceMap.get(line.getVariantId());
            ProductVariant variant = ib.getVariant();

            // Optimistic concurrency check
            if (line.getExpectedVersion() != null && !ib.getVersion().equals(line.getExpectedVersion())) {
                log.warn("Stale version conflict on variant ID {}: expected {}, current {}",
                        variant.getId(), line.getExpectedVersion(), ib.getVersion());
                throw new StaleVersionException("The record changed. Reload before editing.", ib.getVersion());
            }

            int beforeQty = ib.getQuantityOnHand();
            int afterQty;

            if (line.getDirection() == StockMovementDirection.IN) {
                try {
                    afterQty = Math.addExact(beforeQty, line.getQuantity());
                } catch (ArithmeticException e) {
                    throw new InvalidQuantityException("Quantity addition causes arithmetic overflow.");
                }
            } else if (line.getDirection() == StockMovementDirection.OUT) {
                if (beforeQty < line.getQuantity()) {
                    log.warn("Insufficient stock for variant ID {}: current on hand {}, requested deduction {}",
                            variant.getId(), beforeQty, line.getQuantity());
                    throw new InsufficientStockException(
                            variant.getId(),
                            variant.getSku(),
                            variant.getProduct() != null ? variant.getProduct().getName() : "Unknown",
                            line.getQuantity(),
                            beforeQty
                    );
                }
                afterQty = beforeQty - line.getQuantity();
            } else {
                throw new AppException("VALIDATION_FAILED", "Unsupported direction: " + line.getDirection(), HttpStatus.UNPROCESSABLE_ENTITY);
            }

            Long prevVersion = ib.getVersion();
            ib.setQuantityOnHand(afterQty);
            ib.setUpdatedAt(now);
            ib.setUpdatedBy(request.getActorId());

            StockMovement movement = StockMovement.builder()
                    .variant(variant)
                    .businessEvent(eventRef)
                    .movementType(line.getMovementType())
                    .direction(line.getDirection())
                    .quantity(line.getQuantity())
                    .quantityBefore(beforeQty)
                    .quantityAfter(afterQty)
                    .sourceEntityType(request.getSourceEntityType())
                    .sourceEntityId(request.getSourceEntityId())
                    .sourceLineId(line.getSourceLineId())
                    .sourceEffectKey(line.getSourceEffectKey())
                    .reason(line.getLineNote() != null ? line.getLineNote() : request.getReason())
                    .actorId(request.getActorId())
                    .occurredAt(movementOccurredAt)
                    .createdAt(now)
                    .build();

            StockMovement savedMovement = stockMovementRepository.save(movement);

            lineResults.add(PostedLineResult.builder()
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .productName(variant.getProduct() != null ? variant.getProduct().getName() : null)
                    .direction(line.getDirection())
                    .quantity(line.getQuantity())
                    .previousQuantityOnHand(beforeQty)
                    .newQuantityOnHand(afterQty)
                    .previousVersion(prevVersion)
                    .newVersion(prevVersion + 1)
                    .movementId(savedMovement.getId())
                    .build());
        }

        // Save balances
        inventoryBalanceRepository.saveAll(lockedBalances);

        return StockPostingResult.builder()
                .businessEventId(request.getBusinessEventId())
                .lines(lineResults)
                .build();
    }
}
