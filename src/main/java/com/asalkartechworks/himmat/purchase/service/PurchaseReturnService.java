package com.asalkartechworks.himmat.purchase.service;

import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.service.StockPostingLine;
import com.asalkartechworks.himmat.inventory.service.StockPostingRequest;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.service.StockPostingService;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseReturnRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseReturnLineRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseReturnLineResponseDto;
import com.asalkartechworks.himmat.purchase.dto.PurchaseReturnResponseDto;
import com.asalkartechworks.himmat.purchase.model.PaymentAllocation;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseLine;
import com.asalkartechworks.himmat.purchase.model.PurchaseReturn;
import com.asalkartechworks.himmat.purchase.model.PurchaseReturnLine;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
import com.asalkartechworks.himmat.purchase.repository.PaymentAllocationRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseLineRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseReturnLineRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseReturnRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseReturnService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseLineRepository purchaseLineRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnLineRepository purchaseReturnLineRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final StockPostingService stockPostingService;
    private final SupplierLedgerService supplierLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public PurchaseReturnResponseDto createReturn(String idempotencyKey, Long purchaseId, Long actorId, CreatePurchaseReturnRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "PURCHASE_RETURN",
                "Purchase return processing",
                "POST",
                "/api/v1/purchases/" + purchaseId + "/returns",
                request,
                PurchaseReturnResponseDto.class,
                event -> doCreateReturn(event, purchaseId, actorId, request)
        );
    }

    private PurchaseReturnResponseDto doCreateReturn(BusinessEvent event, Long purchaseId, Long actorId, CreatePurchaseReturnRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one return line is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 1. Lock Purchase and Supplier (Order: Supplier Lock first to prevent deadlocks)
        Purchase purchase = purchaseRepository.findByIdWithLock(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with ID: " + purchaseId));

        Supplier supplier = supplierRepository.findByIdWithLock(purchase.getSupplier().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + purchase.getSupplier().getId()));

        if (purchase.getStatus() != PurchaseStatus.POSTED) {
            throw new AppException("INVALID_PURCHASE_STATUS", "Returns can only be created for POSTED purchases", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        LocalDate returnDate = request.getReturnDate() != null ? request.getReturnDate() : businessClock.today();

        Map<Long, PurchaseLine> lineMap = purchase.getLines().stream()
                .collect(Collectors.toMap(PurchaseLine::getId, Function.identity()));

        List<PurchaseReturnLine> returnLines = new ArrayList<>();
        List<StockPostingLine> stockPostingLines = new ArrayList<>();
        BigDecimal totalReturnAmount = BigDecimal.ZERO;

        for (PurchaseReturnLineRequest lineReq : request.getLines()) {
            PurchaseLine pLine = lineMap.get(lineReq.getPurchaseLineId());
            if (pLine == null) {
                throw new AppException("INVALID_PURCHASE_LINE",
                        "Line ID " + lineReq.getPurchaseLineId() + " does not belong to purchase " + purchase.getPurchaseNumber(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            int currentReturned = pLine.getReturnedQuantity() != null ? pLine.getReturnedQuantity() : 0;
            int remainingReturnable = pLine.getQuantity() - currentReturned;

            if (lineReq.getQuantity() > remainingReturnable) {
                throw new AppException("EXCESS_RETURN_QUANTITY",
                        "Requested return quantity (" + lineReq.getQuantity() + ") exceeds remaining returnable quantity (" + remainingReturnable + ") for SKU " + pLine.getSku(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Update line returned quantity
            pLine.setReturnedQuantity(currentReturned + lineReq.getQuantity());
            purchaseLineRepository.save(pLine);

            BigDecimal unitCost = pLine.getGrossUnitCost();
            BigDecimal lineAmount;
            if (lineReq.getQuantity() == remainingReturnable) {
                List<PurchaseReturnLine> prevReturnLines = purchaseReturnLineRepository.findByPurchaseLineId(pLine.getId());
                BigDecimal previouslyReturned = prevReturnLines.stream()
                        .map(PurchaseReturnLine::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal remainingLineAmount = pLine.getLineTotal().subtract(previouslyReturned);
                if (remainingLineAmount.compareTo(BigDecimal.ZERO) > 0) {
                    lineAmount = remainingLineAmount;
                } else {
                    lineAmount = MoneyPolicy.roundMoney(unitCost.multiply(BigDecimal.valueOf(lineReq.getQuantity())));
                }
            } else {
                lineAmount = MoneyPolicy.roundMoney(unitCost.multiply(BigDecimal.valueOf(lineReq.getQuantity())));
            }
            totalReturnAmount = totalReturnAmount.add(lineAmount);

            ProductVariant variant = pLine.getVariant();

            returnLines.add(PurchaseReturnLine.builder()
                    .purchaseLine(pLine)
                    .variant(variant)
                    .returnedQuantity(lineReq.getQuantity())
                    .unitCost(unitCost)
                    .totalAmount(lineAmount)
                    .build());

            stockPostingLines.add(StockPostingLine.builder()
                    .variantId(variant.getId())
                    .quantity(lineReq.getQuantity())
                    .direction(StockMovementDirection.OUT)
                    .movementType(StockMovementType.PURCHASE_RETURN)
                    .build());
        }

        // 2. Generate Return Number (PR-%05d)
        Long nextSeq = purchaseReturnRepository.getNextReturnSequence();
        String returnNumber = String.format("PR-%05d", nextSeq);

        // 3. Save Return Entity
        PurchaseReturn purchaseReturn = PurchaseReturn.builder()
                .returnNumber(returnNumber)
                .purchase(purchase)
                .supplier(supplier)
                .returnDate(returnDate)
                .totalReturnAmount(MoneyPolicy.roundMoney(totalReturnAmount))
                .reason(request.getReason())
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        for (PurchaseReturnLine rl : returnLines) {
            purchaseReturn.addLine(rl);
        }

        PurchaseReturn savedReturn = purchaseReturnRepository.save(purchaseReturn);

        // 4. Stock Posting (Deduce stock with strict non-negative check; allow inactive variants)
        StockPostingRequest postingRequest = StockPostingRequest.builder()
                .lines(stockPostingLines)
                .businessEventId(event.getId())
                .actorId(actorId)
                .occurredAt(now)
                .sourceEntityType("PURCHASE_RETURN")
                .sourceEntityId(savedReturn.getId())
                .reason("Purchase return: " + returnNumber)
                .allowInactiveVariants(true) // Historic returns permitted even if variant is inactive
                .build();

        stockPostingService.postMovements(postingRequest);

        // 5. Release Excess Payment Allocations to Unallocated Credit
        // Net payable remaining on purchase after this return
        int totalOriginalPurchased = purchase.getLines().stream().mapToInt(PurchaseLine::getQuantity).sum();
        int totalReturnedQty = purchase.getLines().stream().mapToInt(PurchaseLine::getReturnedQuantity).sum();
        
        // Sum total return amount across all returns for this purchase
        BigDecimal cumulativeReturns = purchase.getLines().stream()
                .map(l -> MoneyPolicy.roundMoney(l.getGrossUnitCost().multiply(BigDecimal.valueOf(l.getReturnedQuantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPayable = purchase.getTotalPayableAmount().subtract(cumulativeReturns).max(BigDecimal.ZERO);

        if (purchase.getTotalAllocatedAmount().compareTo(netPayable) > 0) {
            BigDecimal excessAllocation = purchase.getTotalAllocatedAmount().subtract(netPayable);
            log.info("Releasing excess payment allocation of {} on purchase {} to unallocated credit",
                    excessAllocation, purchase.getPurchaseNumber());

            List<PaymentAllocation> allocations = paymentAllocationRepository.findByPurchaseIdOrderByIdDesc(purchase.getId());
            BigDecimal remainingToRelease = excessAllocation;

            for (PaymentAllocation alloc : allocations) {
                if (remainingToRelease.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal currentAlloc = alloc.getAllocatedAmount();
                BigDecimal releaseThis = remainingToRelease.min(currentAlloc);

                SupplierPayment pay = alloc.getSupplierPayment();
                SupplierPayment lockedPay = supplierPaymentRepository.findByIdWithLock(pay.getId()).orElse(pay);

                lockedPay.setUnallocatedAmount(lockedPay.getUnallocatedAmount().add(releaseThis));
                supplierPaymentRepository.save(lockedPay);

                if (releaseThis.compareTo(currentAlloc) == 0) {
                    paymentAllocationRepository.delete(alloc);
                } else {
                    alloc.setAllocatedAmount(currentAlloc.subtract(releaseThis));
                    paymentAllocationRepository.save(alloc);
                }

                remainingToRelease = remainingToRelease.subtract(releaseThis);
            }

            purchase.setTotalAllocatedAmount(netPayable);
            purchaseRepository.save(purchase);
        }

        // 6. Post to Supplier Ledger (-totalReturnAmount, negative = reduces payable / adds supplier credit)
        supplierLedgerService.appendLedgerEntry(
                supplier,
                event,
                SupplierLedgerSourceType.PURCHASE_RETURN,
                savedReturn.getId(),
                savedReturn.getTotalReturnAmount().negate(),
                returnDate,
                "Purchase return: " + returnNumber
        );

        return toDto(savedReturn);
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponseDto getReturnById(Long id) {
        PurchaseReturn ret = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found with ID: " + id));
        return toDto(ret);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseReturnResponseDto> searchReturns(Long supplierId, Long purchaseId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return purchaseReturnRepository.searchReturns(supplierId, purchaseId, startDate, endDate, pageable)
                .map(this::toDto);
    }

    public PurchaseReturnResponseDto toDto(PurchaseReturn ret) {
        List<PurchaseReturnLineResponseDto> lineDtos = ret.getLines().stream()
                .map(l -> PurchaseReturnLineResponseDto.builder()
                        .id(l.getId())
                        .purchaseLineId(l.getPurchaseLine().getId())
                        .variantId(l.getVariant().getId())
                        .productName(l.getPurchaseLine().getProductName())
                        .sku(l.getPurchaseLine().getSku())
                        .sizeLabel(l.getPurchaseLine().getSizeLabel())
                        .returnedQuantity(l.getReturnedQuantity())
                        .unitCost(l.getUnitCost())
                        .totalAmount(l.getTotalAmount())
                        .build())
                .toList();

        return PurchaseReturnResponseDto.builder()
                .id(ret.getId())
                .returnNumber(ret.getReturnNumber())
                .purchaseId(ret.getPurchase().getId())
                .purchaseNumber(ret.getPurchase().getPurchaseNumber())
                .supplierId(ret.getSupplier().getId())
                .supplierName(ret.getSupplier().getName())
                .returnDate(ret.getReturnDate())
                .totalReturnAmount(ret.getTotalReturnAmount())
                .reason(ret.getReason())
                .lines(lineDtos)
                .version(ret.getVersion())
                .createdAt(ret.getCreatedAt())
                .build();
    }
}
