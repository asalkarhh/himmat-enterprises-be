package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionLineDto;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionPreviewResponseDto;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionResponseDto;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.ReceiptAllocation;
import com.asalkartechworks.himmat.billing.model.RevisionChangeType;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevision;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevisionLine;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionRepository;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.service.StockPostingLine;
import com.asalkartechworks.himmat.inventory.service.StockPostingRequest;
import com.asalkartechworks.himmat.inventory.service.StockPostingService;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineResponse;
import com.asalkartechworks.himmat.pricing.service.PricingService;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceRevisionService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final SalesInvoiceRevisionRepository salesInvoiceRevisionRepository;
    private final SalesInvoiceRevisionLineRepository salesInvoiceRevisionLineRepository;
    private final CustomerRepository customerRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PricingService pricingService;
    private final StockPostingService stockPostingService;
    private final CustomerLedgerService customerLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InvoiceRevisionPreviewResponseDto previewRevision(Long invoiceId, InvoiceRevisionRequest request) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new AppException("INVALID_INVOICE_STATUS", "Only POSTED invoices can be revised", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Map<Long, SalesInvoiceLine> existingLinesMap = invoice.getLines().stream()
                .filter(SalesInvoiceLine::isActiveInCurrentRevision)
                .collect(Collectors.toMap(SalesInvoiceLine::getId, Function.identity()));

        List<InvoiceRevisionLineDto> previewLines = new ArrayList<>();
        BigDecimal newRunningPayable = BigDecimal.ZERO;

        Map<Long, Boolean> processedLineIds = new HashMap<>();
        int lineIdx = 1;

        for (InvoiceRevisionLineRequest reqLine : request.getLines()) {
            if (reqLine.getInvoiceLineId() != null) {
                SalesInvoiceLine existing = existingLinesMap.get(reqLine.getInvoiceLineId());
                if (existing == null) {
                    throw new AppException("INVALID_INVOICE_LINE",
                            "Line ID " + reqLine.getInvoiceLineId() + " does not belong to invoice " + invoice.getInvoiceNumber(),
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                processedLineIds.put(existing.getId(), true);

                int prevQty = existing.getQuantity();
                int newQty = reqLine.getQuantity();
                int deltaQty = newQty - prevQty;

                if (newQty < existing.getReturnedQuantity()) {
                    throw new AppException("INVALID_REVISION",
                            "Cannot reduce line quantity below already returned quantity (" + existing.getReturnedQuantity() + ") for SKU " + existing.getSku(),
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }

                BigDecimal prevNet = existing.getUnitNetPrice();
                BigDecimal prevTotal = existing.getLineTotal();

                BigDecimal newNet;
                BigDecimal newTotal;
                boolean isOverridden = reqLine.getUnitPriceOverride() != null;

                if (newQty == 0) {
                    // Line removed
                    newNet = BigDecimal.ZERO;
                    newTotal = BigDecimal.ZERO;
                    previewLines.add(InvoiceRevisionLineDto.builder()
                            .invoiceLineId(existing.getId())
                            .variantId(existing.getVariant().getId())
                            .lineIndex(lineIdx++)
                            .changeType(RevisionChangeType.REMOVED)
                            .sku(existing.getSku())
                            .productName(existing.getProductName())
                            .sizeLabel(existing.getSizeLabel())
                            .previousQuantity(prevQty)
                            .newQuantity(0)
                            .deltaQuantity(-prevQty)
                            .previousUnitNetPrice(prevNet)
                            .newUnitNetPrice(newNet)
                            .previousLineTotal(prevTotal)
                            .newLineTotal(newTotal)
                            .deltaLineTotal(newTotal.subtract(prevTotal))
                            .isPriceOverridden(isOverridden)
                            .overrideReason(reqLine.getOverrideReason())
                            .build());
                } else {
                    if (isOverridden) {
                        newNet = reqLine.getUnitPriceOverride();
                    } else if (reqLine.getDiscountPct() != null) {
                        BigDecimal disc = reqLine.getDiscountPct().divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
                        newNet = existing.getBaseUnitPrice().multiply(BigDecimal.ONE.subtract(disc));
                    } else {
                        newNet = prevNet;
                    }
                    newTotal = MoneyPolicy.roundMoney(newNet.multiply(BigDecimal.valueOf(newQty)));
                    newRunningPayable = newRunningPayable.add(newTotal);

                    RevisionChangeType changeType = (prevQty == newQty && prevTotal.compareTo(newTotal) == 0)
                            ? RevisionChangeType.UNCHANGED : RevisionChangeType.MODIFIED;

                    previewLines.add(InvoiceRevisionLineDto.builder()
                            .invoiceLineId(existing.getId())
                            .variantId(existing.getVariant().getId())
                            .lineIndex(lineIdx++)
                            .changeType(changeType)
                            .sku(existing.getSku())
                            .productName(existing.getProductName())
                            .sizeLabel(existing.getSizeLabel())
                            .previousQuantity(prevQty)
                            .newQuantity(newQty)
                            .deltaQuantity(deltaQty)
                            .previousUnitNetPrice(prevNet)
                            .newUnitNetPrice(newNet)
                            .previousLineTotal(prevTotal)
                            .newLineTotal(newTotal)
                            .deltaLineTotal(newTotal.subtract(prevTotal))
                            .isPriceOverridden(isOverridden)
                            .overrideReason(reqLine.getOverrideReason())
                            .build());
                }
            } else {
                // Newly added line
                ProductVariant variant = productVariantRepository.findByIdWithHierarchy(reqLine.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found with ID: " + reqLine.getVariantId()));

                PricingPreviewLineRequest pReq = PricingPreviewLineRequest.builder()
                        .variantId(variant.getId())
                        .quantity(reqLine.getQuantity())
                        .unitPriceOverride(reqLine.getUnitPriceOverride())
                        .discountPct(reqLine.getDiscountPct())
                        .overrideReason(reqLine.getOverrideReason())
                        .build();

                PricingPreviewLineResponse pResp = pricingService.priceLine(variant, pReq, true);
                BigDecimal newTotal = pResp.getLineTotal();
                newRunningPayable = newRunningPayable.add(newTotal);

                previewLines.add(InvoiceRevisionLineDto.builder()
                        .variantId(variant.getId())
                        .lineIndex(lineIdx++)
                        .changeType(RevisionChangeType.ADDED)
                        .sku(variant.getSku())
                        .productName(variant.getProduct() != null ? variant.getProduct().getName() : "Unknown")
                        .sizeLabel(variant.getSizeLabel())
                        .previousQuantity(0)
                        .newQuantity(reqLine.getQuantity())
                        .deltaQuantity(reqLine.getQuantity())
                        .previousUnitNetPrice(BigDecimal.ZERO)
                        .newUnitNetPrice(pResp.getUnitNetInclusive())
                        .previousLineTotal(BigDecimal.ZERO)
                        .newLineTotal(newTotal)
                        .deltaLineTotal(newTotal)
                        .isPriceOverridden(reqLine.getUnitPriceOverride() != null)
                        .overrideReason(reqLine.getOverrideReason())
                        .build());
            }
        }

        // Check for any omitted existing lines (implicitly REMOVED)
        for (SalesInvoiceLine existing : existingLinesMap.values()) {
            if (!processedLineIds.containsKey(existing.getId())) {
                if (existing.getReturnedQuantity() > 0) {
                    throw new AppException("INVALID_REVISION",
                            "Cannot remove line with returned quantity (" + existing.getReturnedQuantity() + ") for SKU " + existing.getSku(),
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                previewLines.add(InvoiceRevisionLineDto.builder()
                        .invoiceLineId(existing.getId())
                        .variantId(existing.getVariant().getId())
                        .lineIndex(lineIdx++)
                        .changeType(RevisionChangeType.REMOVED)
                        .sku(existing.getSku())
                        .productName(existing.getProductName())
                        .sizeLabel(existing.getSizeLabel())
                        .previousQuantity(existing.getQuantity())
                        .newQuantity(0)
                        .deltaQuantity(-existing.getQuantity())
                        .previousUnitNetPrice(existing.getUnitNetPrice())
                        .newUnitNetPrice(BigDecimal.ZERO)
                        .previousLineTotal(existing.getLineTotal())
                        .newLineTotal(BigDecimal.ZERO)
                        .deltaLineTotal(existing.getLineTotal().negate())
                        .isPriceOverridden(false)
                        .build());
            }
        }

        newRunningPayable = MoneyPolicy.roundMoney(newRunningPayable);
        BigDecimal deltaPayable = MoneyPolicy.roundMoney(newRunningPayable.subtract(invoice.getTotalPayableAmount()));

        BigDecimal effectiveNewPayable = newRunningPayable.subtract(invoice.getTotalReturnedAmount()).max(BigDecimal.ZERO);
        BigDecimal releasedAlloc = BigDecimal.ZERO;
        BigDecimal currentAlloc = invoice.getTotalAllocatedAmount() != null ? invoice.getTotalAllocatedAmount() : BigDecimal.ZERO;

        if (currentAlloc.compareTo(effectiveNewPayable) > 0) {
            releasedAlloc = currentAlloc.subtract(effectiveNewPayable);
        }

        BigDecimal newAlloc = currentAlloc.subtract(releasedAlloc);
        BigDecimal newOutstanding = effectiveNewPayable.subtract(newAlloc).max(BigDecimal.ZERO);

        return InvoiceRevisionPreviewResponseDto.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .revisionNumber((invoice.getRevisionCount() != null ? invoice.getRevisionCount() : 0) + 1)
                .previousTotalPayable(invoice.getTotalPayableAmount())
                .newTotalPayable(newRunningPayable)
                .deltaPayableAmount(deltaPayable)
                .previousAllocatedAmount(currentAlloc)
                .releasedAllocationAmount(releasedAlloc)
                .newAllocatedAmount(newAlloc)
                .newOutstandingAmount(newOutstanding)
                .lines(previewLines)
                .build();
    }

    public InvoiceRevisionResponseDto createRevision(String idempotencyKey, Long invoiceId, Long actorId, InvoiceRevisionRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "INVOICE_REVISION",
                "Customer invoice revision: " + request.getReason(),
                "POST",
                "/api/v1/invoices/" + invoiceId + "/revisions",
                request,
                InvoiceRevisionResponseDto.class,
                event -> doCreateRevision(event, invoiceId, actorId, request)
        );
    }

    private InvoiceRevisionResponseDto doCreateRevision(BusinessEvent event, Long invoiceId, Long actorId, InvoiceRevisionRequest request) {
        // 1. Fetch & Lock Customer first (Party Lock Ordering)
        SalesInvoice invoiceUnchecked = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + invoiceId));

        Customer customer = customerRepository.findByIdWithLock(invoiceUnchecked.getCustomer().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + invoiceUnchecked.getCustomer().getId()));

        // 2. Lock Invoice
        SalesInvoice invoice = salesInvoiceRepository.findByIdWithLock(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new AppException("INVALID_INVOICE_STATUS", "Only POSTED invoices can be revised", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 3. Optimistic version check
        if (request.getExpectedVersion() != null && !request.getExpectedVersion().equals(invoice.getVersion())) {
            throw new StaleVersionException("The invoice has been modified by another transaction. Reload before revising.", invoice.getVersion());
        }

        Instant now = businessClock.now();
        int revisionNumber = (invoice.getRevisionCount() != null ? invoice.getRevisionCount() : 0) + 1;

        // 4. Capture snapshot payload of previous invoice state before modifications
        String snapshotPayload = buildSnapshotPayload(invoice);

        // 5. Line matching & calculation
        Map<Long, SalesInvoiceLine> existingLinesMap = invoice.getLines().stream()
                .filter(SalesInvoiceLine::isActiveInCurrentRevision)
                .collect(Collectors.toMap(SalesInvoiceLine::getId, Function.identity()));

        List<SalesInvoiceRevisionLine> revisionLines = new ArrayList<>();
        List<StockPostingLine> stockOutLines = new ArrayList<>();
        List<StockPostingLine> stockInLines = new ArrayList<>();

        BigDecimal previousSubtotal = invoice.getSubtotalExclTax();
        BigDecimal previousDiscount = invoice.getTotalDiscountAmount();
        BigDecimal previousTaxable = invoice.getTotalTaxableAmount();
        BigDecimal previousGst = invoice.getTotalGstAmount();
        BigDecimal previousPayable = invoice.getTotalPayableAmount();

        BigDecimal runningGrossTotal = BigDecimal.ZERO;
        BigDecimal runningTotalPayable = BigDecimal.ZERO;
        BigDecimal runningTaxableAmount = BigDecimal.ZERO;
        BigDecimal runningTaxAmount = BigDecimal.ZERO;
        boolean allTaxAvailable = true;

        Map<Long, Boolean> processedLineIds = new HashMap<>();
        int lineIdx = 1;

        for (InvoiceRevisionLineRequest reqLine : request.getLines()) {
            if (reqLine.getInvoiceLineId() != null) {
                SalesInvoiceLine existing = existingLinesMap.get(reqLine.getInvoiceLineId());
                if (existing == null) {
                    throw new AppException("INVALID_INVOICE_LINE",
                            "Line ID " + reqLine.getInvoiceLineId() + " does not belong to invoice " + invoice.getInvoiceNumber(),
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                processedLineIds.put(existing.getId(), true);

                int prevQty = existing.getQuantity();
                int newQty = reqLine.getQuantity();
                int deltaQty = newQty - prevQty;

                if (newQty < existing.getReturnedQuantity()) {
                    throw new AppException("INVALID_REVISION",
                            "Cannot reduce line quantity below already returned quantity (" + existing.getReturnedQuantity() + ") for SKU " + existing.getSku(),
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }

                BigDecimal prevNet = existing.getUnitNetPrice();
                BigDecimal prevTotal = existing.getLineTotal();

                if (newQty == 0) {
                    // Line removed
                    existing.setActiveInCurrentRevision(false);
                    existing.setQuantity(0);
                    existing.setLineTotal(BigDecimal.ZERO);
                    salesInvoiceLineRepository.save(existing);

                    if (prevQty > 0) {
                        stockInLines.add(StockPostingLine.builder()
                                .variantId(existing.getVariant().getId())
                                .quantity(prevQty)
                                .direction(StockMovementDirection.IN)
                                .movementType(StockMovementType.REVERSAL)
                                .sourceLineId(existing.getId())
                                .lineNote("Line removed in revision #" + revisionNumber)
                                .build());
                    }

                    revisionLines.add(SalesInvoiceRevisionLine.builder()
                            .invoiceLine(existing)
                            .variant(existing.getVariant())
                            .lineIndex(lineIdx++)
                            .changeType(RevisionChangeType.REMOVED)
                            .previousQuantity(prevQty)
                            .newQuantity(0)
                            .deltaQuantity(-prevQty)
                            .previousUnitNetPrice(prevNet)
                            .newUnitNetPrice(BigDecimal.ZERO)
                            .previousLineTotal(prevTotal)
                            .newLineTotal(BigDecimal.ZERO)
                            .deltaLineTotal(prevTotal.negate())
                            .isPriceOverridden(reqLine.getUnitPriceOverride() != null)
                            .overrideReason(reqLine.getOverrideReason())
                            .build());
                } else {
                    BigDecimal newBase = existing.getBaseUnitPrice();
                    BigDecimal newDiscPct = existing.getDiscountPct();
                    BigDecimal newNet;
                    boolean isOverridden = reqLine.getUnitPriceOverride() != null;

                    if (isOverridden) {
                        newNet = reqLine.getUnitPriceOverride();
                        existing.setPriceOverridden(true);
                        existing.setOverrideReason(reqLine.getOverrideReason());
                    } else if (reqLine.getDiscountPct() != null) {
                        newDiscPct = reqLine.getDiscountPct();
                        BigDecimal discMultiplier = newDiscPct.divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
                        newNet = newBase.multiply(BigDecimal.ONE.subtract(discMultiplier));
                        existing.setDiscountPct(newDiscPct);
                    } else {
                        newNet = prevNet;
                    }

                    BigDecimal newLineTotal = MoneyPolicy.roundMoney(newNet.multiply(BigDecimal.valueOf(newQty)));
                    BigDecimal lineGross = MoneyPolicy.roundMoney(newBase.multiply(BigDecimal.valueOf(newQty)));

                    runningGrossTotal = runningGrossTotal.add(lineGross);
                    runningTotalPayable = runningTotalPayable.add(newLineTotal);

                    existing.setQuantity(newQty);
                    existing.setUnitNetPrice(newNet);
                    existing.setLineTotal(newLineTotal);

                    if (existing.isTaxBreakdownAvailable() && existing.getGstRatePct() != null) {
                        BigDecimal taxable = MoneyPolicy.computeTaxableFromInclusive(newLineTotal, existing.getGstRatePct());
                        BigDecimal tax = MoneyPolicy.roundMoney(newLineTotal.subtract(taxable));
                        existing.setTaxableAmount(taxable);
                        existing.setTaxAmount(tax);
                        runningTaxableAmount = runningTaxableAmount.add(taxable);
                        runningTaxAmount = runningTaxAmount.add(tax);
                    } else {
                        allTaxAvailable = false;
                    }

                    salesInvoiceLineRepository.save(existing);

                    if (deltaQty > 0) {
                        stockOutLines.add(StockPostingLine.builder()
                                .variantId(existing.getVariant().getId())
                                .quantity(deltaQty)
                                .direction(StockMovementDirection.OUT)
                                .movementType(StockMovementType.SALE)
                                .sourceLineId(existing.getId())
                                .lineNote("Quantity increased in revision #" + revisionNumber)
                                .build());
                    } else if (deltaQty < 0) {
                        stockInLines.add(StockPostingLine.builder()
                                .variantId(existing.getVariant().getId())
                                .quantity(Math.abs(deltaQty))
                                .direction(StockMovementDirection.IN)
                                .movementType(StockMovementType.REVERSAL)
                                .sourceLineId(existing.getId())
                                .lineNote("Quantity decreased in revision #" + revisionNumber)
                                .build());
                    }

                    RevisionChangeType changeType = (prevQty == newQty && prevTotal.compareTo(newLineTotal) == 0)
                            ? RevisionChangeType.UNCHANGED : RevisionChangeType.MODIFIED;

                    revisionLines.add(SalesInvoiceRevisionLine.builder()
                            .invoiceLine(existing)
                            .variant(existing.getVariant())
                            .lineIndex(lineIdx++)
                            .changeType(changeType)
                            .previousQuantity(prevQty)
                            .newQuantity(newQty)
                            .deltaQuantity(deltaQty)
                            .previousUnitNetPrice(prevNet)
                            .newUnitNetPrice(newNet)
                            .previousLineTotal(prevTotal)
                            .newLineTotal(newLineTotal)
                            .deltaLineTotal(newLineTotal.subtract(prevTotal))
                            .isPriceOverridden(isOverridden)
                            .overrideReason(reqLine.getOverrideReason())
                            .build());
                }
            } else {
                // Newly added line
                ProductVariant variant = productVariantRepository.findByIdWithHierarchy(reqLine.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found with ID: " + reqLine.getVariantId()));

                if (!variant.isActive() || (variant.getProduct() != null && !variant.getProduct().isActive())) {
                    throw new AppException("INACTIVE_VARIANT", "Variant [" + variant.getSku() + "] is inactive.", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                PricingPreviewLineRequest pReq = PricingPreviewLineRequest.builder()
                        .variantId(variant.getId())
                        .quantity(reqLine.getQuantity())
                        .unitPriceOverride(reqLine.getUnitPriceOverride())
                        .discountPct(reqLine.getDiscountPct())
                        .overrideReason(reqLine.getOverrideReason())
                        .build();

                PricingPreviewLineResponse pResp = pricingService.priceLine(variant, pReq, true);

                BigDecimal lineGross = MoneyPolicy.roundMoney(pResp.getBaseInclusivePrice().multiply(BigDecimal.valueOf(reqLine.getQuantity())));
                runningGrossTotal = runningGrossTotal.add(lineGross);
                runningTotalPayable = runningTotalPayable.add(pResp.getLineTotal());

                if (pResp.isTaxBreakdownAvailable() && pResp.getTaxableAmount() != null) {
                    runningTaxableAmount = runningTaxableAmount.add(pResp.getTaxableAmount());
                    runningTaxAmount = runningTaxAmount.add(pResp.getTaxAmount());
                } else {
                    allTaxAvailable = false;
                }

                SalesInvoiceLine newLine = SalesInvoiceLine.builder()
                        .invoice(invoice)
                        .variant(variant)
                        .lineIndex(lineIdx++)
                        .productName(variant.getProduct() != null ? variant.getProduct().getName() : "Unknown")
                        .sku(variant.getSku())
                        .sizeLabel(variant.getSizeLabel())
                        .pricingMethod(variant.getPricingMethod())
                        .quantity(reqLine.getQuantity())
                        .baseUnitPrice(pResp.getBaseInclusivePrice())
                        .discountPct(pResp.getDiscountPct())
                        .discountSource(pResp.getDiscountSource())
                        .unitNetPrice(pResp.getUnitNetInclusive())
                        .lineTotal(pResp.getLineTotal())
                        .returnedQuantity(0)
                        .isActiveInCurrentRevision(true)
                        .isPriceOverridden(reqLine.getUnitPriceOverride() != null)
                        .overrideReason(reqLine.getOverrideReason())
                        .gstRatePct(pResp.getGstRatePct())
                        .taxableAmount(pResp.getTaxableAmount())
                        .taxAmount(pResp.getTaxAmount())
                        .taxBreakdownAvailable(pResp.isTaxBreakdownAvailable())
                        .purchaseCostExclTax(variant.getPurchaseCostExclTax())
                        .costAvailable(pResp.isCostAvailable())
                        .build();

                SalesInvoiceLine savedLine = salesInvoiceLineRepository.save(newLine);
                invoice.addLine(savedLine);

                stockOutLines.add(StockPostingLine.builder()
                        .variantId(variant.getId())
                        .quantity(reqLine.getQuantity())
                        .direction(StockMovementDirection.OUT)
                        .movementType(StockMovementType.SALE)
                        .sourceLineId(savedLine.getId())
                        .lineNote("Line added in revision #" + revisionNumber)
                        .build());

                revisionLines.add(SalesInvoiceRevisionLine.builder()
                        .invoiceLine(savedLine)
                        .variant(variant)
                        .lineIndex(savedLine.getLineIndex())
                        .changeType(RevisionChangeType.ADDED)
                        .previousQuantity(0)
                        .newQuantity(reqLine.getQuantity())
                        .deltaQuantity(reqLine.getQuantity())
                        .previousUnitNetPrice(BigDecimal.ZERO)
                        .newUnitNetPrice(pResp.getUnitNetInclusive())
                        .previousLineTotal(BigDecimal.ZERO)
                        .newLineTotal(pResp.getLineTotal())
                        .deltaLineTotal(pResp.getLineTotal())
                        .isPriceOverridden(reqLine.getUnitPriceOverride() != null)
                        .overrideReason(reqLine.getOverrideReason())
                        .build());
            }
        }

        // Check for implicitly omitted lines
        for (SalesInvoiceLine existing : existingLinesMap.values()) {
            if (!processedLineIds.containsKey(existing.getId())) {
                if (existing.getReturnedQuantity() > 0) {
                    throw new AppException("INVALID_REVISION",
                            "Cannot remove line with returned quantity (" + existing.getReturnedQuantity() + ") for SKU " + existing.getSku(),
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                existing.setActiveInCurrentRevision(false);
                int prevQty = existing.getQuantity();
                existing.setQuantity(0);
                existing.setLineTotal(BigDecimal.ZERO);
                salesInvoiceLineRepository.save(existing);

                if (prevQty > 0) {
                    stockInLines.add(StockPostingLine.builder()
                            .variantId(existing.getVariant().getId())
                            .quantity(prevQty)
                            .direction(StockMovementDirection.IN)
                            .movementType(StockMovementType.REVERSAL)
                            .sourceLineId(existing.getId())
                            .lineNote("Line omitted in revision #" + revisionNumber)
                            .build());
                }

                revisionLines.add(SalesInvoiceRevisionLine.builder()
                        .invoiceLine(existing)
                        .variant(existing.getVariant())
                        .lineIndex(lineIdx++)
                        .changeType(RevisionChangeType.REMOVED)
                        .previousQuantity(prevQty)
                        .newQuantity(0)
                        .deltaQuantity(-prevQty)
                        .previousUnitNetPrice(existing.getUnitNetPrice())
                        .newUnitNetPrice(BigDecimal.ZERO)
                        .previousLineTotal(existing.getLineTotal())
                        .newLineTotal(BigDecimal.ZERO)
                        .deltaLineTotal(existing.getLineTotal().negate())
                        .isPriceOverridden(false)
                        .build());
            }
        }

        // 6. Header totals & delta calculations
        runningGrossTotal = MoneyPolicy.roundMoney(runningGrossTotal);
        BigDecimal newTotalPayable = MoneyPolicy.roundMoney(runningTotalPayable);
        BigDecimal newTotalDiscount = MoneyPolicy.roundMoney(runningGrossTotal.subtract(newTotalPayable));
        BigDecimal newTotalTaxable = allTaxAvailable ? MoneyPolicy.roundMoney(runningTaxableAmount) : null;
        BigDecimal newTotalGst = allTaxAvailable ? MoneyPolicy.roundMoney(runningTaxAmount) : null;
        BigDecimal newSubtotal = newTotalTaxable;

        BigDecimal deltaPayable = MoneyPolicy.roundMoney(newTotalPayable.subtract(previousPayable));

        // 7. Stock Posting
        // Post OUT movements first (SALE, requires active variants)
        if (!stockOutLines.isEmpty()) {
            stockPostingService.postMovements(StockPostingRequest.builder()
                    .lines(stockOutLines)
                    .businessEventId(event.getId())
                    .actorId(actorId)
                    .occurredAt(now)
                    .sourceEntityType("INVOICE_REVISION")
                    .sourceEntityId(invoice.getId())
                    .reason("Invoice " + invoice.getInvoiceNumber() + " revision #" + revisionNumber)
                    .allowInactiveVariants(false)
                    .build());
        }

        // Post IN movements (REVERSAL, allows historical variants even if deactivated)
        if (!stockInLines.isEmpty()) {
            stockPostingService.postMovements(StockPostingRequest.builder()
                    .lines(stockInLines)
                    .businessEventId(event.getId())
                    .actorId(actorId)
                    .occurredAt(now)
                    .sourceEntityType("INVOICE_REVISION")
                    .sourceEntityId(invoice.getId())
                    .reason("Invoice " + invoice.getInvoiceNumber() + " revision #" + revisionNumber)
                    .allowInactiveVariants(true)
                    .build());
        }

        // 8. Release excess allocations if new payable reduced below allocated amount
        BigDecimal effectiveNewPayable = newTotalPayable.subtract(invoice.getTotalReturnedAmount()).max(BigDecimal.ZERO);
        BigDecimal releasedAlloc = BigDecimal.ZERO;
        BigDecimal currentAlloc = invoice.getTotalAllocatedAmount() != null ? invoice.getTotalAllocatedAmount() : BigDecimal.ZERO;

        if (currentAlloc.compareTo(effectiveNewPayable) > 0) {
            BigDecimal excessToRelease = currentAlloc.subtract(effectiveNewPayable);
            log.info("Releasing excess payment allocation of {} on invoice {} due to revision #{}",
                    excessToRelease, invoice.getInvoiceNumber(), revisionNumber);

            List<ReceiptAllocation> allocations = receiptAllocationRepository.findByInvoiceIdOrderByIdDesc(invoice.getId());
            BigDecimal remainingToRelease = excessToRelease;

            for (ReceiptAllocation alloc : allocations) {
                if (remainingToRelease.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal allocAmt = alloc.getAllocatedAmount();
                BigDecimal releaseThis = remainingToRelease.min(allocAmt);

                CustomerReceipt receipt = customerReceiptRepository.findByIdWithLock(alloc.getCustomerReceipt().getId())
                        .orElse(alloc.getCustomerReceipt());
                receipt.setUnallocatedAmount(receipt.getUnallocatedAmount().add(releaseThis));
                receipt.setUpdatedAt(now);
                receipt.setUpdatedBy(actorId);
                customerReceiptRepository.save(receipt);

                if (releaseThis.compareTo(allocAmt) == 0) {
                    receiptAllocationRepository.delete(alloc);
                } else {
                    alloc.setAllocatedAmount(allocAmt.subtract(releaseThis));
                    receiptAllocationRepository.save(alloc);
                }

                remainingToRelease = remainingToRelease.subtract(releaseThis);
                releasedAlloc = releasedAlloc.add(releaseThis);
            }

            invoice.setTotalAllocatedAmount(effectiveNewPayable);
        }

        // Update settlement status
        BigDecimal finalAlloc = invoice.getTotalAllocatedAmount();
        if (effectiveNewPayable.compareTo(BigDecimal.ZERO) == 0 || finalAlloc.compareTo(effectiveNewPayable) >= 0) {
            invoice.setSettlementStatus(SettlementStatus.PAID);
        } else if (finalAlloc.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setSettlementStatus(SettlementStatus.PARTIALLY_PAID);
        } else {
            invoice.setSettlementStatus(SettlementStatus.UNPAID);
        }

        // 9. Post Customer Ledger Entry for delta payable
        if (deltaPayable.compareTo(BigDecimal.ZERO) != 0) {
            customerLedgerService.appendLedgerEntry(
                    customer,
                    event,
                    CustomerLedgerSourceType.INVOICE,
                    invoice.getId(),
                    deltaPayable,
                    invoice.getInvoiceDate(),
                    "Invoice revision #" + revisionNumber + ": " + request.getReason()
            );
        }

        // 10. Save Revision Header and Lines
        SalesInvoiceRevision revision = SalesInvoiceRevision.builder()
                .invoice(invoice)
                .revisionNumber(revisionNumber)
                .reason(request.getReason())
                .businessEvent(event)
                .actorId(actorId)
                .previousSubtotalExclTax(previousSubtotal)
                .previousTotalDiscount(previousDiscount)
                .previousTotalTaxable(previousTaxable)
                .previousTotalGst(previousGst)
                .previousTotalPayable(previousPayable)
                .newSubtotalExclTax(newSubtotal)
                .newTotalDiscount(newTotalDiscount)
                .newTotalTaxable(newTotalTaxable)
                .newTotalGst(newTotalGst)
                .newTotalPayable(newTotalPayable)
                .deltaPayableAmount(deltaPayable)
                .releasedAllocationAmount(releasedAlloc)
                .snapshotPayload(snapshotPayload)
                .createdAt(now)
                .build();

        for (SalesInvoiceRevisionLine rl : revisionLines) {
            revision.addLine(rl);
        }

        SalesInvoiceRevision savedRevision = salesInvoiceRevisionRepository.save(revision);

        // 11. Update Invoice entity
        invoice.setRevisionCount(revisionNumber);
        invoice.setTotalPayableAmount(newTotalPayable);
        invoice.setTotalDiscountAmount(newTotalDiscount);
        invoice.setTotalTaxableAmount(newTotalTaxable);
        invoice.setTotalGstAmount(newTotalGst);
        invoice.setSubtotalExclTax(newSubtotal);
        invoice.setTaxBreakdownAvailable(allTaxAvailable);
        invoice.setUpdatedAt(now);
        invoice.setUpdatedBy(actorId);
        salesInvoiceRepository.save(invoice);

        return toDto(savedRevision);
    }

    @Transactional(readOnly = true)
    public List<InvoiceRevisionResponseDto> getRevisionsByInvoiceId(Long invoiceId) {
        return salesInvoiceRevisionRepository.findByInvoiceIdOrderByRevisionNumberAsc(invoiceId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceRevisionResponseDto getRevisionById(Long revisionId) {
        SalesInvoiceRevision rev = salesInvoiceRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice revision not found with ID: " + revisionId));
        return toDto(rev);
    }

    private String buildSnapshotPayload(SalesInvoice invoice) {
        Map<String, Object> map = new HashMap<>();
        map.put("invoiceNumber", invoice.getInvoiceNumber());
        map.put("revisionCount", invoice.getRevisionCount());
        map.put("totalPayableAmount", invoice.getTotalPayableAmount());
        map.put("totalAllocatedAmount", invoice.getTotalAllocatedAmount());
        map.put("totalReturnedAmount", invoice.getTotalReturnedAmount());
        map.put("settlementStatus", invoice.getSettlementStatus().name());
        map.put("lines", invoice.getLines().stream()
                .filter(SalesInvoiceLine::isActiveInCurrentRevision)
                .map(l -> Map.of(
                        "id", l.getId(),
                        "sku", l.getSku(),
                        "quantity", l.getQuantity(),
                        "unitNetPrice", l.getUnitNetPrice(),
                        "lineTotal", l.getLineTotal()
                ))
                .toList());

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize snapshot payload: {}", e.getMessage());
            return "{}";
        }
    }

    public InvoiceRevisionResponseDto toDto(SalesInvoiceRevision rev) {
        List<InvoiceRevisionLineDto> lineDtos = rev.getLines().stream()
                .map(l -> InvoiceRevisionLineDto.builder()
                        .id(l.getId())
                        .invoiceLineId(l.getInvoiceLine().getId())
                        .variantId(l.getVariant().getId())
                        .lineIndex(l.getLineIndex())
                        .changeType(l.getChangeType())
                        .sku(l.getVariant().getSku())
                        .productName(l.getVariant().getProduct() != null ? l.getVariant().getProduct().getName() : "Unknown")
                        .sizeLabel(l.getVariant().getSizeLabel())
                        .previousQuantity(l.getPreviousQuantity())
                        .newQuantity(l.getNewQuantity())
                        .deltaQuantity(l.getDeltaQuantity())
                        .previousUnitNetPrice(l.getPreviousUnitNetPrice())
                        .newUnitNetPrice(l.getNewUnitNetPrice())
                        .previousLineTotal(l.getPreviousLineTotal())
                        .newLineTotal(l.getNewLineTotal())
                        .deltaLineTotal(l.getDeltaLineTotal())
                        .isPriceOverridden(l.isPriceOverridden())
                        .overrideReason(l.getOverrideReason())
                        .build())
                .toList();

        return InvoiceRevisionResponseDto.builder()
                .id(rev.getId())
                .invoiceId(rev.getInvoice().getId())
                .invoiceNumber(rev.getInvoice().getInvoiceNumber())
                .revisionNumber(rev.getRevisionNumber())
                .reason(rev.getReason())
                .businessEventId(rev.getBusinessEvent().getId())
                .actorId(rev.getActorId())
                .previousSubtotalExclTax(rev.getPreviousSubtotalExclTax())
                .previousTotalDiscount(rev.getPreviousTotalDiscount())
                .previousTotalTaxable(rev.getPreviousTotalTaxable())
                .previousTotalGst(rev.getPreviousTotalGst())
                .previousTotalPayable(rev.getPreviousTotalPayable())
                .newSubtotalExclTax(rev.getNewSubtotalExclTax())
                .newTotalDiscount(rev.getNewTotalDiscount())
                .newTotalTaxable(rev.getNewTotalTaxable())
                .newTotalGst(rev.getNewTotalGst())
                .newTotalPayable(rev.getNewTotalPayable())
                .deltaPayableAmount(rev.getDeltaPayableAmount())
                .releasedAllocationAmount(rev.getReleasedAllocationAmount())
                .snapshotPayload(rev.getSnapshotPayload())
                .lines(lineDtos)
                .createdAt(rev.getCreatedAt())
                .build();
    }
}
