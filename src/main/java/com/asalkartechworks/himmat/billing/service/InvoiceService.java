package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineDto;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceResponseDto;
import com.asalkartechworks.himmat.billing.dto.ReceiptAllocationDto;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.ReceiptAllocation;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.service.StockPostingLine;
import com.asalkartechworks.himmat.inventory.service.StockPostingRequest;
import com.asalkartechworks.himmat.inventory.service.StockPostingService;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerSite;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.party.repository.CustomerSiteRepository;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineResponse;
import com.asalkartechworks.himmat.pricing.service.PricingService;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSiteRepository customerSiteRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockPostingService stockPostingService;
    private final PricingService pricingService;
    private final CustomerLedgerService customerLedgerService;
    private final CustomerReceiptService customerReceiptService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public InvoiceResponseDto createInvoice(String idempotencyKey, Long actorId, CreateInvoiceRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "SALES_INVOICE_CREATE",
                "Customer sales invoice creation",
                "POST",
                "/api/v1/invoices",
                request,
                InvoiceResponseDto.class,
                event -> doCreateInvoice(event, actorId, request)
        );
    }

    private InvoiceResponseDto doCreateInvoice(BusinessEvent event, Long actorId, CreateInvoiceRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "Invoice must contain at least one line", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 1. Lock Customer (Party Lock - First in Lock Ordering)
        Customer customer = customerRepository.findByIdWithLock(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + request.getCustomerId()));

        if (!customer.isActive()) {
            throw new AppException("INACTIVE_CUSTOMER", "Cannot create invoice for inactive customer", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 2. Validate optional Site
        CustomerSite site = null;
        if (request.getSiteId() != null) {
            site = customerSiteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerSite not found with ID: " + request.getSiteId()));

            if (!site.getCustomer().getId().equals(customer.getId())) {
                throw new AppException("INVALID_CUSTOMER_SITE",
                        "Site ID " + request.getSiteId() + " does not belong to customer " + customer.getId(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (!site.isActive()) {
                throw new AppException("INACTIVE_CUSTOMER_SITE", "Cannot bill against inactive customer site", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        // 3. Date validation: Reject future invoice dates
        LocalDate today = businessClock.today();
        LocalDate invoiceDate = request.getInvoiceDate() != null ? request.getInvoiceDate() : today;
        if (invoiceDate.isAfter(today)) {
            throw new AppException("FUTURE_INVOICE_DATE", "Invoice date cannot be in the future", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        LocalDate dueDate = invoiceDate.plusDays(30);

        Instant now = businessClock.now();
        Long nextSeq = salesInvoiceRepository.getNextInvoiceSequence();
        String invoiceNumber = String.format("INV-%05d", nextSeq);

        // 4. Calculate lines and build stock posting lines
        List<SalesInvoiceLine> linesToSave = new ArrayList<>();
        List<StockPostingLine> stockPostingLines = new ArrayList<>();
        StringBuilder fingerprintBuilder = new StringBuilder();

        BigDecimal runningGrossTotal = BigDecimal.ZERO;
        BigDecimal runningTotalPayable = BigDecimal.ZERO;
        BigDecimal runningTaxableAmount = BigDecimal.ZERO;
        BigDecimal runningTaxAmount = BigDecimal.ZERO;
        boolean allTaxAvailable = true;

        int lineIdx = 1;
        for (InvoiceLineRequest lineReq : request.getLines()) {
            ProductVariant variant = productVariantRepository.findByIdWithHierarchy(lineReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with ID: " + lineReq.getVariantId()));

            if (!variant.isActive() || (variant.getProduct() != null && !variant.getProduct().isActive())) {
                throw new AppException("INACTIVE_VARIANT", "Variant [" + variant.getSku() + "] or its product is inactive.", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (lineReq.getExpectedVariantVersion() != null && !variant.getVersion().equals(lineReq.getExpectedVariantVersion())) {
                throw new StaleVersionException("Product variant definition changed. Reload before creating invoice.", variant.getVersion());
            }

            PricingPreviewLineRequest pricingReq = PricingPreviewLineRequest.builder()
                    .variantId(variant.getId())
                    .quantity(lineReq.getQuantity())
                    .unitPriceOverride(lineReq.getUnitPriceOverride())
                    .discountPct(lineReq.getDiscountPct())
                    .overrideReason(lineReq.getOverrideReason())
                    .build();

            PricingPreviewLineResponse pResp = pricingService.priceLine(variant, pricingReq, true);

            BigDecimal lineGross = MoneyPolicy.roundMoney(pResp.getBaseInclusivePrice().multiply(BigDecimal.valueOf(lineReq.getQuantity())));
            runningGrossTotal = runningGrossTotal.add(lineGross);
            runningTotalPayable = runningTotalPayable.add(pResp.getLineTotal());

            if (pResp.isTaxBreakdownAvailable() && pResp.getTaxableAmount() != null && pResp.getTaxAmount() != null) {
                runningTaxableAmount = runningTaxableAmount.add(pResp.getTaxableAmount());
                runningTaxAmount = runningTaxAmount.add(pResp.getTaxAmount());
            } else {
                allTaxAvailable = false;
            }

            SalesInvoiceLine line = SalesInvoiceLine.builder()
                    .variant(variant)
                    .lineIndex(lineIdx)
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
                    .isPriceOverridden(lineReq.getUnitPriceOverride() != null)
                    .overrideReason(lineReq.getOverrideReason())
                    .gstRatePct(pResp.getGstRatePct())
                    .taxableAmount(pResp.getTaxableAmount())
                    .taxAmount(pResp.getTaxAmount())
                    .taxBreakdownAvailable(pResp.isTaxBreakdownAvailable())
                    .purchaseCostExclTax(variant.getPurchaseCostExclTax())
                    .costAvailable(pResp.isCostAvailable())
                    .build();

            linesToSave.add(line);

            stockPostingLines.add(StockPostingLine.builder()
                    .variantId(variant.getId())
                    .quantity(lineReq.getQuantity())
                    .direction(StockMovementDirection.OUT)
                    .movementType(StockMovementType.SALE)
                    .sourceEffectKey("SALE-" + invoiceNumber + "-LINE-" + lineIdx)
                    .expectedVersion(lineReq.getExpectedVariantVersion())
                    .build());

            fingerprintBuilder.append(variant.getId())
                    .append(":")
                    .append(variant.getVersion())
                    .append(":")
                    .append(pResp.getUnitNetInclusive().toPlainString())
                    .append(":")
                    .append(pResp.getLineTotal().toPlainString())
                    .append(";");

            lineIdx++;
        }

        String computedFingerprint = InvoicePreviewService.generateFingerprint(fingerprintBuilder.toString());
        if (request.getPricingFingerprint() != null && !request.getPricingFingerprint().isBlank()) {
            if (!request.getPricingFingerprint().equals(computedFingerprint)) {
                throw new AppException("PRICING_CHANGED",
                        "Catalog prices or variant definitions changed since preview. Please refresh and review.",
                        HttpStatus.CONFLICT);
            }
        }

        // 5. Header totals & snapshot
        runningGrossTotal = MoneyPolicy.roundMoney(runningGrossTotal);
        runningTotalPayable = MoneyPolicy.roundMoney(runningTotalPayable);
        BigDecimal totalDiscount = MoneyPolicy.roundMoney(runningGrossTotal.subtract(runningTotalPayable));

        BigDecimal totalTaxable = allTaxAvailable ? MoneyPolicy.roundMoney(runningTaxableAmount) : null;
        BigDecimal totalGst = allTaxAvailable ? MoneyPolicy.roundMoney(runningTaxAmount) : null;
        BigDecimal subtotalExclTax = totalTaxable;

        SalesInvoice invoice = SalesInvoice.builder()
                .invoiceNumber(invoiceNumber)
                .customer(customer)
                .customerSite(site)
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .displayFormat(request.getDisplayFormat() != null ? request.getDisplayFormat() : InvoiceDisplayFormat.NORMAL)
                .status(InvoiceStatus.POSTED)
                .settlementStatus(runningTotalPayable.compareTo(BigDecimal.ZERO) == 0 ? SettlementStatus.PAID : SettlementStatus.UNPAID)
                .customerName(customer.getName())
                .customerPhone(customer.getPhone())
                .customerGstin(customer.getGstin())
                .customerAddress(formatCustomerAddress(customer))
                .siteName(site != null ? site.getSiteName() : null)
                .siteAddress(site != null ? formatSiteAddress(site) : null)
                .subtotalExclTax(subtotalExclTax)
                .totalDiscountAmount(totalDiscount)
                .totalTaxableAmount(totalTaxable)
                .totalGstAmount(totalGst)
                .totalPayableAmount(runningTotalPayable)
                .totalAllocatedAmount(BigDecimal.ZERO)
                .taxBreakdownAvailable(allTaxAvailable)
                .pricingFingerprint(computedFingerprint)
                .notes(request.getNotes())
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        for (SalesInvoiceLine line : linesToSave) {
            invoice.addLine(line);
        }

        SalesInvoice savedInvoice = salesInvoiceRepository.save(invoice);

        // 6. Atomic Stock Deduction (Enforces non-negative stock invariant)
        stockPostingService.postMovements(StockPostingRequest.builder()
                .lines(stockPostingLines)
                .businessEventId(event.getId())
                .actorId(actorId)
                .occurredAt(now)
                .sourceEntityType("SALES_INVOICE")
                .sourceEntityId(savedInvoice.getId())
                .reason("Sales invoice: " + invoiceNumber)
                .allowInactiveVariants(false)
                .build());

        // 7. Append Customer Ledger Entry (+totalPayableAmount: customer owes shop)
        customerLedgerService.appendLedgerEntry(
                customer,
                event,
                CustomerLedgerSourceType.INVOICE,
                savedInvoice.getId(),
                savedInvoice.getTotalPayableAmount(),
                invoiceDate,
                "Sales invoice: " + invoiceNumber
        );

        // 8. Handle Upfront Receipt or Advance Credit Application
        BigDecimal allocatedSoFar = BigDecimal.ZERO;

        // 8a. Upfront receipt components
        if (request.getReceiptComponents() != null && !request.getReceiptComponents().isEmpty()) {
            BigDecimal componentTotal = request.getReceiptComponents().stream()
                    .map(PaymentComponentDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (componentTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal allocToInvoice = componentTotal.min(savedInvoice.getTotalPayableAmount());
                List<InvoiceAllocationRequest> allocList = new ArrayList<>();
                if (allocToInvoice.compareTo(BigDecimal.ZERO) > 0) {
                    allocList.add(InvoiceAllocationRequest.builder()
                            .invoiceId(savedInvoice.getId())
                            .amount(allocToInvoice)
                            .build());
                }

                customerReceiptService.processReceiptDirect(
                        event,
                        customer,
                        actorId,
                        invoiceDate,
                        request.getReceiptComponents(),
                        componentTotal,
                        allocList,
                        "Immediate payment for invoice " + invoiceNumber
                );

                allocatedSoFar = allocatedSoFar.add(allocToInvoice);
            }
        }

        // 8b. Apply unallocated advance credit if requested
        if (request.getApplyAdvanceAmount() != null && request.getApplyAdvanceAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal advanceToApply = request.getApplyAdvanceAmount();
            BigDecimal remainingOutstanding = savedInvoice.getTotalPayableAmount().subtract(allocatedSoFar);

            if (advanceToApply.compareTo(remainingOutstanding) > 0) {
                throw new AppException("EXCESS_ALLOCATION",
                        "applyAdvanceAmount (" + advanceToApply + ") exceeds remaining invoice balance (" + remainingOutstanding + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            List<CustomerReceipt> unallocatedReceipts = customerReceiptRepository.findUnallocatedReceiptsByCustomerWithLock(customer.getId());
            BigDecimal totalAvailableAdvance = unallocatedReceipts.stream()
                    .map(CustomerReceipt::getUnallocatedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (advanceToApply.compareTo(totalAvailableAdvance) > 0) {
                throw new AppException("INSUFFICIENT_UNALLOCATED_CREDIT",
                        "Requested advance (" + advanceToApply + ") exceeds available customer credit (" + totalAvailableAdvance + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            BigDecimal remainingAdvanceToDeduct = advanceToApply;
            for (CustomerReceipt rec : unallocatedReceipts) {
                if (remainingAdvanceToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal deduct = rec.getUnallocatedAmount().min(remainingAdvanceToDeduct);
                rec.setUnallocatedAmount(MoneyPolicy.roundMoney(rec.getUnallocatedAmount().subtract(deduct)));
                rec.setUpdatedAt(now);
                rec.setUpdatedBy(actorId);
                customerReceiptRepository.save(rec);

                ReceiptAllocation allocation = ReceiptAllocation.builder()
                        .customerReceipt(rec)
                        .invoice(savedInvoice)
                        .allocatedAmount(MoneyPolicy.roundMoney(deduct))
                        .businessEvent(event)
                        .createdAt(now)
                        .build();
                receiptAllocationRepository.save(allocation);

                remainingAdvanceToDeduct = remainingAdvanceToDeduct.subtract(deduct);
                allocatedSoFar = allocatedSoFar.add(deduct);
            }

            savedInvoice.setTotalAllocatedAmount(MoneyPolicy.roundMoney(allocatedSoFar));
            salesInvoiceRepository.save(savedInvoice);
        }

        // Re-evaluate settlement status
        savedInvoice.setTotalAllocatedAmount(MoneyPolicy.roundMoney(allocatedSoFar));
        if (savedInvoice.getTotalPayableAmount().compareTo(BigDecimal.ZERO) == 0 ||
                savedInvoice.getTotalAllocatedAmount().compareTo(savedInvoice.getTotalPayableAmount()) >= 0) {
            savedInvoice.setSettlementStatus(SettlementStatus.PAID);
        } else if (savedInvoice.getTotalAllocatedAmount().compareTo(BigDecimal.ZERO) > 0) {
            savedInvoice.setSettlementStatus(SettlementStatus.PARTIALLY_PAID);
        } else {
            savedInvoice.setSettlementStatus(SettlementStatus.UNPAID);
        }
        savedInvoice = salesInvoiceRepository.save(savedInvoice);

        return toDto(savedInvoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponseDto getInvoiceById(Long id) {
        SalesInvoice invoice = salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + id));
        return toDto(invoice);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponseDto> searchInvoices(Long customerId,
                                                   Long siteId,
                                                   InvoiceStatus status,
                                                   SettlementStatus settlementStatus,
                                                   InvoiceDisplayFormat displayFormat,
                                                   LocalDate startDate,
                                                   LocalDate endDate,
                                                   Boolean overdueOnly,
                                                   String search,
                                                   Pageable pageable) {
        LocalDate today = businessClock.today();
        return salesInvoiceRepository.searchInvoices(
                customerId, siteId, status, settlementStatus, displayFormat,
                startDate, endDate, overdueOnly, today, search, pageable
        ).map(this::toDto);
    }

    public InvoiceResponseDto toDto(SalesInvoice invoice) {
        LocalDate today = businessClock.today();
        BigDecimal outstanding = MoneyPolicy.roundMoney(
                invoice.getTotalPayableAmount().subtract(
                        invoice.getTotalAllocatedAmount() != null ? invoice.getTotalAllocatedAmount() : BigDecimal.ZERO
                )
        );

        boolean isOverdue = invoice.getStatus() == InvoiceStatus.POSTED &&
                outstanding.compareTo(BigDecimal.ZERO) > 0 &&
                today.isAfter(invoice.getDueDate());

        long daysOverdue = isOverdue ? ChronoUnit.DAYS.between(invoice.getDueDate(), today) : 0;

        List<InvoiceLineDto> lineDtos = invoice.getLines().stream()
                .map(l -> InvoiceLineDto.builder()
                        .id(l.getId())
                        .variantId(l.getVariant().getId())
                        .lineIndex(l.getLineIndex())
                        .productName(l.getProductName())
                        .sku(l.getSku())
                        .sizeLabel(l.getSizeLabel())
                        .pricingMethod(l.getPricingMethod())
                        .quantity(l.getQuantity())
                        .baseUnitPrice(l.getBaseUnitPrice())
                        .discountPct(l.getDiscountPct())
                        .discountSource(l.getDiscountSource())
                        .unitNetPrice(l.getUnitNetPrice())
                        .lineTotal(l.getLineTotal())
                        .isPriceOverridden(l.isPriceOverridden())
                        .overrideReason(l.getOverrideReason())
                        .gstRatePct(l.getGstRatePct())
                        .taxableAmount(l.getTaxableAmount())
                        .taxAmount(l.getTaxAmount())
                        .taxBreakdownAvailable(l.isTaxBreakdownAvailable())
                        .purchaseCostExclTax(l.getPurchaseCostExclTax())
                        .costAvailable(l.isCostAvailable())
                        .build())
                .toList();

        List<ReceiptAllocation> allocations = receiptAllocationRepository.findByInvoiceId(invoice.getId());
        List<ReceiptAllocationDto> allocDtos = allocations.stream()
                .map(a -> ReceiptAllocationDto.builder()
                        .id(a.getId())
                        .receiptId(a.getCustomerReceipt().getId())
                        .receiptNumber(a.getCustomerReceipt().getReceiptNumber())
                        .invoiceId(invoice.getId())
                        .invoiceNumber(invoice.getInvoiceNumber())
                        .allocatedAmount(a.getAllocatedAmount())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomerName())
                .customerPhone(invoice.getCustomerPhone())
                .customerGstin(invoice.getCustomerGstin())
                .customerAddress(invoice.getCustomerAddress())
                .siteId(invoice.getCustomerSite() != null ? invoice.getCustomerSite().getId() : null)
                .siteName(invoice.getSiteName())
                .siteAddress(invoice.getSiteAddress())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .displayFormat(invoice.getDisplayFormat())
                .status(invoice.getStatus())
                .settlementStatus(invoice.getSettlementStatus())
                .subtotalExclTax(invoice.getSubtotalExclTax())
                .totalDiscountAmount(invoice.getTotalDiscountAmount())
                .totalTaxableAmount(invoice.getTotalTaxableAmount())
                .totalGstAmount(invoice.getTotalGstAmount())
                .totalPayableAmount(invoice.getTotalPayableAmount())
                .totalAllocatedAmount(invoice.getTotalAllocatedAmount())
                .outstandingAmount(outstanding)
                .taxBreakdownAvailable(invoice.isTaxBreakdownAvailable())
                .pricingFingerprint(invoice.getPricingFingerprint())
                .notes(invoice.getNotes())
                .isOverdue(isOverdue)
                .daysOverdue(daysOverdue)
                .lines(lineDtos)
                .allocations(allocDtos)
                .businessEventId(invoice.getBusinessEvent().getId())
                .version(invoice.getVersion())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .createdBy(invoice.getCreatedBy())
                .updatedBy(invoice.getUpdatedBy())
                .build();
    }

    private String formatCustomerAddress(Customer customer) {
        StringBuilder sb = new StringBuilder();
        if (customer.getAddressLine1() != null && !customer.getAddressLine1().isBlank()) {
            sb.append(customer.getAddressLine1());
        }
        if (customer.getAddressLine2() != null && !customer.getAddressLine2().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(customer.getAddressLine2());
        }
        if (customer.getCity() != null && !customer.getCity().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(customer.getCity());
        }
        if (customer.getPostalCode() != null && !customer.getPostalCode().isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(customer.getPostalCode());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String formatSiteAddress(CustomerSite site) {
        StringBuilder sb = new StringBuilder();
        if (site.getAddressLine1() != null && !site.getAddressLine1().isBlank()) {
            sb.append(site.getAddressLine1());
        }
        if (site.getAddressLine2() != null && !site.getAddressLine2().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(site.getAddressLine2());
        }
        if (site.getCity() != null && !site.getCity().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(site.getCity());
        }
        if (site.getPostalCode() != null && !site.getPostalCode().isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(site.getPostalCode());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
