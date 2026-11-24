package com.asalkartechworks.himmat.purchase.service;

import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.InactiveVariantException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.service.StockPostingLine;
import com.asalkartechworks.himmat.inventory.service.StockPostingRequest;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.service.StockPostingService;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierPaymentRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentAllocationRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseLineRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseLineResponseDto;
import com.asalkartechworks.himmat.purchase.dto.PurchaseResponseDto;
import com.asalkartechworks.himmat.purchase.model.PriceBasis;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseLine;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockPostingService stockPostingService;
    private final SupplierLedgerService supplierLedgerService;
    private final SupplierPaymentService supplierPaymentService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public PurchaseResponseDto createPurchase(String idempotencyKey, Long actorId, CreatePurchaseRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "PURCHASE_CREATE",
                "Inward purchase creation",
                "POST",
                "/api/v1/purchases",
                request,
                PurchaseResponseDto.class,
                event -> doCreatePurchase(event, actorId, request)
        );
    }

    private PurchaseResponseDto doCreatePurchase(BusinessEvent event, Long actorId, CreatePurchaseRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "Purchase must contain at least one line", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 1. Lock supplier (Party Lock - First in Lock Ordering)
        Supplier supplier = supplierRepository.findByIdWithLock(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + request.getSupplierId()));

        if (!supplier.isActive()) {
            throw new AppException("INACTIVE_SUPPLIER", "Cannot create purchase for inactive supplier", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        LocalDate purchaseDate = request.getPurchaseDate() != null ? request.getPurchaseDate() : businessClock.today();

        // 2. Build and calculate lines
        List<PurchaseLine> lines = new ArrayList<>();
        List<StockPostingLine> stockPostingLines = new ArrayList<>();
        BigDecimal runningSubtotalExclTax = BigDecimal.ZERO;
        BigDecimal runningTotalDiscount = BigDecimal.ZERO;
        BigDecimal runningTotalGst = BigDecimal.ZERO;
        boolean allLinesHaveGst = true;
        BigDecimal runningTotalPayable = BigDecimal.ZERO;

        int lineIdx = 1;
        for (PurchaseLineRequest lineReq : request.getLines()) {
            ProductVariant variant = productVariantRepository.findById(lineReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with ID: " + lineReq.getVariantId()));

            if (!variant.isActive() || (variant.getProduct() != null && !variant.getProduct().isActive())) {
                throw new InactiveVariantException("Variant [" + variant.getSku() + "] or its product is inactive.");
            }

            // Default discount from category purchase discount if not specified
            BigDecimal discountPct = lineReq.getDiscountPct();
            if (discountPct == null) {
                Category cat = (variant.getProduct() != null && variant.getProduct().getSubcategory() != null)
                        ? variant.getProduct().getSubcategory().getCategory()
                        : null;
                discountPct = cat != null && cat.getPurchaseDiscountPct() != null ? cat.getPurchaseDiscountPct() : BigDecimal.ZERO;
            }

            BigDecimal enteredUnitPrice = lineReq.getEnteredUnitPrice();
            BigDecimal discountPerUnit = enteredUnitPrice.multiply(discountPct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal grossUnitCost;
            BigDecimal netUnitCost;
            BigDecimal taxPerUnit;

            if (lineReq.getPriceBasis() == PriceBasis.GST_EXCLUSIVE) {
                if (lineReq.getGstRatePct() == null) {
                    throw new AppException("GST_RATE_REQUIRED", "GST rate is required when price basis is GST_EXCLUSIVE", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                BigDecimal discountedExclTax = enteredUnitPrice.subtract(discountPerUnit);
                taxPerUnit = discountedExclTax.multiply(lineReq.getGstRatePct()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                grossUnitCost = discountedExclTax.add(taxPerUnit);
                netUnitCost = discountedExclTax;
            } else { // GST_INCLUSIVE
                BigDecimal discountedInclTax = enteredUnitPrice.subtract(discountPerUnit);
                grossUnitCost = discountedInclTax;
                if (lineReq.getGstRatePct() != null) {
                    BigDecimal taxFactor = BigDecimal.ONE.add(lineReq.getGstRatePct().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                    netUnitCost = discountedInclTax.divide(taxFactor, 4, RoundingMode.HALF_UP);
                    taxPerUnit = grossUnitCost.subtract(netUnitCost);
                } else {
                    netUnitCost = null;
                    taxPerUnit = null;
                    allLinesHaveGst = false;
                    if (Boolean.TRUE.equals(lineReq.getUpdateProductCost())) {
                        throw new AppException("GST_RATE_REQUIRED_FOR_COST_UPDATE",
                                "GST rate is required to update product purchase cost when price basis is GST_INCLUSIVE",
                                HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                }
            }

            BigDecimal lineTotal = MoneyPolicy.roundMoney(grossUnitCost.multiply(BigDecimal.valueOf(lineReq.getQuantity())));
            runningTotalPayable = runningTotalPayable.add(lineTotal);
            runningTotalDiscount = runningTotalDiscount.add(MoneyPolicy.roundMoney(discountPerUnit.multiply(BigDecimal.valueOf(lineReq.getQuantity()))));

            if (netUnitCost != null) {
                runningSubtotalExclTax = runningSubtotalExclTax.add(MoneyPolicy.roundMoney(netUnitCost.multiply(BigDecimal.valueOf(lineReq.getQuantity()))));
            } else {
                runningSubtotalExclTax = runningSubtotalExclTax.add(lineTotal);
            }

            if (taxPerUnit != null) {
                runningTotalGst = runningTotalGst.add(MoneyPolicy.roundMoney(taxPerUnit.multiply(BigDecimal.valueOf(lineReq.getQuantity()))));
            }

            // Update product purchase cost if requested
            if (Boolean.TRUE.equals(lineReq.getUpdateProductCost())) {
                if (lineReq.getVariantVersion() != null && !variant.getVersion().equals(lineReq.getVariantVersion())) {
                    throw new StaleVersionException("Product variant was modified by another operation. Reload before updating cost.", variant.getVersion());
                }
                variant.setPurchaseCostExclTax(MoneyPolicy.roundUnitRate(netUnitCost));
                variant.setUpdatedAt(now);
                variant.setUpdatedBy(actorId);
                productVariantRepository.save(variant);
            }

            Product product = variant.getProduct();
            PurchaseLine line = PurchaseLine.builder()
                    .variant(variant)
                    .lineIndex(lineIdx++)
                    .productName(product != null ? product.getName() : "Unknown Product")
                    .sku(variant.getSku())
                    .sizeLabel(variant.getSizeLabel())
                    .quantity(lineReq.getQuantity())
                    .returnedQuantity(0)
                    .priceBasis(lineReq.getPriceBasis())
                    .enteredUnitPrice(lineReq.getEnteredUnitPrice())
                    .discountPct(discountPct)
                    .gstRatePct(lineReq.getGstRatePct())
                    .netUnitCost(netUnitCost != null ? MoneyPolicy.roundUnitRate(netUnitCost) : null)
                    .grossUnitCost(MoneyPolicy.roundUnitRate(grossUnitCost))
                    .lineTotal(lineTotal)
                    .updateProductCost(Boolean.TRUE.equals(lineReq.getUpdateProductCost()))
                    .build();

            lines.add(line);

            stockPostingLines.add(StockPostingLine.builder()
                    .variantId(variant.getId())
                    .quantity(lineReq.getQuantity())
                    .direction(StockMovementDirection.IN)
                    .movementType(StockMovementType.PURCHASE_RECEIPT)
                    .expectedVersion(lineReq.getVariantVersion())
                    .build());
        }

        // 3. Generate Purchase Number (PO-%05d)
        Long nextSeq = purchaseRepository.getNextPurchaseSequence();
        String purchaseNumber = String.format("PO-%05d", nextSeq);

        // 4. Build and Save Purchase Entity
        Purchase purchase = Purchase.builder()
                .purchaseNumber(purchaseNumber)
                .supplier(supplier)
                .supplierInvoiceNumber(request.getSupplierInvoiceNumber())
                .purchaseDate(purchaseDate)
                .status(PurchaseStatus.POSTED)
                .supplierName(supplier.getName())
                .supplierContact(supplier.getPhone())
                .supplierGstin(supplier.getGstin())
                .subtotalExclTax(MoneyPolicy.roundMoney(runningSubtotalExclTax))
                .totalDiscountAmount(MoneyPolicy.roundMoney(runningTotalDiscount))
                .totalGstAmount(allLinesHaveGst ? MoneyPolicy.roundMoney(runningTotalGst) : null)
                .totalPayableAmount(MoneyPolicy.roundMoney(runningTotalPayable))
                .totalAllocatedAmount(BigDecimal.ZERO)
                .notes(request.getNotes())
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        for (PurchaseLine l : lines) {
            purchase.addLine(l);
        }

        Purchase savedPurchase = purchaseRepository.save(purchase);

        // 5. Post Stock Movements (Atomic via StockPostingService)
        StockPostingRequest postingRequest = StockPostingRequest.builder()
                .lines(stockPostingLines)
                .businessEventId(event.getId())
                .actorId(actorId)
                .occurredAt(now)
                .sourceEntityType("PURCHASE_INVOICE")
                .sourceEntityId(savedPurchase.getId())
                .reason("Purchase receipt: " + purchaseNumber)
                .allowInactiveVariants(false)
                .build();

        stockPostingService.postMovements(postingRequest);

        // 6. Post to Supplier Ledger (+totalPayableAmount, positive = shop owes supplier)
        supplierLedgerService.appendLedgerEntry(
                supplier,
                event,
                SupplierLedgerSourceType.PURCHASE,
                savedPurchase.getId(),
                savedPurchase.getTotalPayableAmount(),
                purchaseDate,
                "Inward purchase invoice: " + purchaseNumber
        );

        // 7. Process immediate payment components if attached
        if (request.getPaymentComponents() != null && !request.getPaymentComponents().isEmpty()) {
            BigDecimal totalPayment = request.getPaymentComponents().stream()
                    .map(c -> c.getAmount() != null ? c.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalPayment.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal allocateToThis = totalPayment.min(savedPurchase.getTotalPayableAmount());
                List<PaymentAllocationRequest> allocList = new ArrayList<>();
                if (allocateToThis.compareTo(BigDecimal.ZERO) > 0) {
                    allocList.add(PaymentAllocationRequest.builder()
                            .purchaseId(savedPurchase.getId())
                            .amount(allocateToThis)
                            .build());
                }

                CreateSupplierPaymentRequest payReq = CreateSupplierPaymentRequest.builder()
                        .paymentDate(purchaseDate)
                        .totalAmount(totalPayment)
                        .notes("Payment with purchase " + purchaseNumber)
                        .components(request.getPaymentComponents())
                        .allocations(allocList)
                        .build();

                supplierPaymentService.processPaymentDirect(event, supplier, actorId, payReq);
                // Reload purchase to reflect allocated amount
                savedPurchase = purchaseRepository.findById(savedPurchase.getId()).orElse(savedPurchase);
            }
        }

        return toDto(savedPurchase);
    }

    @Transactional(readOnly = true)
    public PurchaseResponseDto getPurchaseById(Long id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with ID: " + id));
        return toDto(purchase);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseResponseDto> searchPurchases(Long supplierId,
                                                    PurchaseStatus status,
                                                    LocalDate startDate,
                                                    LocalDate endDate,
                                                    String search,
                                                    Pageable pageable) {
        return purchaseRepository.searchPurchases(supplierId, status, startDate, endDate, search, pageable)
                .map(this::toDto);
    }

    public PurchaseResponseDto toDto(Purchase purchase) {
        BigDecimal outstanding = purchase.getOutstandingAmount();

        List<PurchaseLineResponseDto> lineDtos = purchase.getLines().stream()
                .map(l -> PurchaseLineResponseDto.builder()
                        .id(l.getId())
                        .variantId(l.getVariant().getId())
                        .lineIndex(l.getLineIndex())
                        .productName(l.getProductName())
                        .sku(l.getSku())
                        .sizeLabel(l.getSizeLabel())
                        .quantity(l.getQuantity())
                        .returnedQuantity(l.getReturnedQuantity())
                        .remainingQuantity(l.getRemainingQuantity())
                        .priceBasis(l.getPriceBasis())
                        .enteredUnitPrice(l.getEnteredUnitPrice())
                        .discountPct(l.getDiscountPct())
                        .gstRatePct(l.getGstRatePct())
                        .netUnitCost(l.getNetUnitCost())
                        .grossUnitCost(l.getGrossUnitCost())
                        .lineTotal(l.getLineTotal())
                        .updateProductCost(l.isUpdateProductCost())
                        .build())
                .toList();

        return PurchaseResponseDto.builder()
                .id(purchase.getId())
                .purchaseNumber(purchase.getPurchaseNumber())
                .supplierId(purchase.getSupplier().getId())
                .supplierName(purchase.getSupplierName())
                .supplierContact(purchase.getSupplierContact())
                .supplierGstin(purchase.getSupplierGstin())
                .supplierInvoiceNumber(purchase.getSupplierInvoiceNumber())
                .purchaseDate(purchase.getPurchaseDate())
                .status(purchase.getStatus())
                .subtotalExclTax(purchase.getSubtotalExclTax())
                .totalDiscountAmount(purchase.getTotalDiscountAmount())
                .totalGstAmount(purchase.getTotalGstAmount())
                .totalPayableAmount(purchase.getTotalPayableAmount())
                .totalAllocatedAmount(purchase.getTotalAllocatedAmount())
                .outstandingBalance(outstanding)
                .notes(purchase.getNotes())
                .lines(lineDtos)
                .version(purchase.getVersion())
                .createdAt(purchase.getCreatedAt())
                .updatedAt(purchase.getUpdatedAt())
                .createdBy(purchase.getCreatedBy())
                .updatedBy(purchase.getUpdatedBy())
                .build();
    }
}
