package com.asalkartechworks.himmat.inventory.service;

import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.DuplicateVariantLineException;
import com.asalkartechworks.himmat.common.errors.InvalidQuantityException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.dto.CreateManualAdjustmentRequest;
import com.asalkartechworks.himmat.inventory.dto.ManualAdjustmentLineRequest;
import com.asalkartechworks.himmat.inventory.dto.ManualAdjustmentLineResultDto;
import com.asalkartechworks.himmat.inventory.dto.ManualAdjustmentResponse;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualAdjustmentService {

    private final StockPostingService stockPostingService;
    private final BusinessTransactionExecutor transactionExecutor;
    private final BusinessClock businessClock;

    public ManualAdjustmentResponse adjustStock(String idempotencyKey,
                                                Long actorId,
                                                CreateManualAdjustmentRequest request) {
        validateManualAdjustmentRequest(request);

        String route = "/api/v1/inventory/adjustments";
        String httpMethod = "POST";
        String eventType = "MANUAL_STOCK_ADJUSTMENT";

        return transactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                eventType,
                request.getReason(),
                httpMethod,
                route,
                request,
                ManualAdjustmentResponse.class,
                event -> {
                    List<StockPostingLine> postingLines = new ArrayList<>();
                    for (int i = 0; i < request.getLines().size(); i++) {
                        ManualAdjustmentLineRequest line = request.getLines().get(i);
                        StockMovementType movementType = (line.getDirection() == StockMovementDirection.IN)
                                ? StockMovementType.MANUAL_ADJUSTMENT_IN
                                : StockMovementType.MANUAL_ADJUSTMENT_OUT;

                        String effectKey = "MANUAL_ADJUSTMENT:" + event.getId() + ":" + line.getVariantId();

                        postingLines.add(StockPostingLine.builder()
                                .variantId(line.getVariantId())
                                .movementType(movementType)
                                .direction(line.getDirection())
                                .quantity(line.getQuantity())
                                .expectedVersion(line.getExpectedVersion())
                                .sourceLineId((long) i)
                                .sourceEffectKey(effectKey)
                                .build());
                    }

                    StockPostingRequest postingRequest = StockPostingRequest.builder()
                            .businessEventId(event.getId())
                            .actorId(actorId)
                            .occurredAt(businessClock.now())
                            .reason(request.getReason())
                            .sourceEntityType("MANUAL_ADJUSTMENT")
                            .sourceEntityId(event.getId())
                            .allowInactiveVariants(false)
                            .lines(postingLines)
                            .build();

                    StockPostingResult postingResult = stockPostingService.postMovements(postingRequest);

                    List<ManualAdjustmentLineResultDto> lineResults = postingResult.getLines().stream()
                            .map(pl -> ManualAdjustmentLineResultDto.builder()
                                    .variantId(pl.getVariantId())
                                    .sku(pl.getSku())
                                    .productName(pl.getProductName())
                                    .direction(pl.getDirection())
                                    .quantity(pl.getQuantity())
                                    .previousQuantityOnHand(pl.getPreviousQuantityOnHand())
                                    .newQuantityOnHand(pl.getNewQuantityOnHand())
                                    .previousVersion(pl.getPreviousVersion())
                                    .newVersion(pl.getNewVersion())
                                    .movementId(pl.getMovementId())
                                    .build())
                            .toList();

                    return ManualAdjustmentResponse.builder()
                            .eventId(event.getId())
                            .eventKey(event.getRequestKey())
                            .reason(request.getReason())
                            .occurredAt(event.getOccurredAt())
                            .lines(lineResults)
                            .build();
                }
        );
    }

    private void validateManualAdjustmentRequest(CreateManualAdjustmentRequest request) {
        if (request == null) {
            throw new AppException("VALIDATION_FAILED", "Adjustment request cannot be null.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request.getReason() == null || request.getReason().trim().length() < 3) {
            throw new AppException("VALIDATION_FAILED", "Adjustment reason must be at least 3 characters.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "Adjustment must contain at least one line.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request.getLines().size() > 100) {
            throw new AppException("VALIDATION_FAILED", "Adjustment cannot contain more than 100 lines.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Set<Long> seenVariantIds = new HashSet<>();
        for (ManualAdjustmentLineRequest line : request.getLines()) {
            if (line.getVariantId() == null) {
                throw new AppException("VALIDATION_FAILED", "variantId is required on every line.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (!seenVariantIds.add(line.getVariantId())) {
                throw new DuplicateVariantLineException("Duplicate variant ID [" + line.getVariantId() + "] in adjustment lines. A variant cannot appear more than once in a manual adjustment.");
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new InvalidQuantityException("Adjustment quantity must be a positive integer greater than zero.");
            }
            if (line.getDirection() == null) {
                throw new AppException("VALIDATION_FAILED", "Direction (IN or OUT) is required on every line.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (line.getExpectedVersion() == null) {
                throw new AppException("VALIDATION_FAILED", "expectedVersion is required on every line.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }
}
