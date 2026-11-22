package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.CustomerReturnLineDto;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnLineRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnPreviewResponseDto;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnResponseDto;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerReturn;
import com.asalkartechworks.himmat.billing.model.CustomerReturnLine;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.ReceiptAllocation;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevisionLine;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReturnLineRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReturnRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionLineRepository;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.common.dto.PageResponse;
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
public class CustomerReturnService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final SalesInvoiceRevisionLineRepository salesInvoiceRevisionLineRepository;
    private final CustomerReturnRepository customerReturnRepository;
    private final CustomerReturnLineRepository customerReturnLineRepository;
    private final CustomerRepository customerRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final StockPostingService stockPostingService;
    private final CustomerLedgerService customerLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public CustomerReturnPreviewResponseDto previewReturn(Long invoiceId, CustomerReturnRequest request) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new AppException("INVALID_INVOICE_STATUS", "Returns can only be recorded for POSTED invoices", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Map<Long, SalesInvoiceLine> lineMap = invoice.getLines().stream()
                .collect(Collectors.toMap(SalesInvoiceLine::getId, Function.identity()));

        List<CustomerReturnLineDto> previewLines = new ArrayList<>();
        BigDecimal runningReturnTotal = BigDecimal.ZERO;

        for (CustomerReturnLineRequest reqLine : request.getLines()) {
            SalesInvoiceLine line = lineMap.get(reqLine.getInvoiceLineId());
            if (line == null) {
                throw new AppException("INVALID_INVOICE_LINE",
                        "Line ID " + reqLine.getInvoiceLineId() + " does not belong to invoice " + invoice.getInvoiceNumber(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            int remaining = line.getRemainingReturnableQuantity();
            if (reqLine.getQuantity() > remaining) {
                throw new AppException("EXCESS_RETURN_QUANTITY",
                        "Requested return quantity (" + reqLine.getQuantity() + ") exceeds remaining returnable quantity (" + remaining + ") for SKU " + line.getSku(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ValuationResult valuation = resolveValuation(line, request.getValuationPolicy(), reqLine.getValuationBasis());
            BigDecimal lineAmount = MoneyPolicy.roundMoney(valuation.unitPrice.multiply(BigDecimal.valueOf(reqLine.getQuantity())));
            runningReturnTotal = runningReturnTotal.add(lineAmount);

            BigDecimal taxable = null;
            BigDecimal tax = null;
            if (line.isTaxBreakdownAvailable() && line.getGstRatePct() != null) {
                taxable = MoneyPolicy.computeTaxableFromInclusive(lineAmount, line.getGstRatePct());
                tax = MoneyPolicy.roundMoney(lineAmount.subtract(taxable));
            }

            previewLines.add(CustomerReturnLineDto.builder()
                    .invoiceLineId(line.getId())
                    .variantId(line.getVariant().getId())
                    .sku(line.getSku())
                    .productName(line.getProductName())
                    .sizeLabel(line.getSizeLabel())
                    .returnedQuantity(reqLine.getQuantity())
                    .unitPrice(valuation.unitPrice)
                    .totalAmount(lineAmount)
                    .valuationBasis(valuation.basis)
                    .gstRatePct(line.getGstRatePct())
                    .taxableAmount(taxable)
                    .taxAmount(tax)
                    .build());
        }

        runningReturnTotal = MoneyPolicy.roundMoney(runningReturnTotal);
        BigDecimal newEffectivePayable = invoice.getTotalPayableAmount().subtract(invoice.getTotalReturnedAmount().add(runningReturnTotal)).max(BigDecimal.ZERO);

        BigDecimal currentAlloc = invoice.getTotalAllocatedAmount() != null ? invoice.getTotalAllocatedAmount() : BigDecimal.ZERO;
        BigDecimal releasedAlloc = BigDecimal.ZERO;

        if (currentAlloc.compareTo(newEffectivePayable) > 0) {
            releasedAlloc = currentAlloc.subtract(newEffectivePayable);
        }

        BigDecimal newAlloc = currentAlloc.subtract(releasedAlloc);
        BigDecimal newOutstanding = newEffectivePayable.subtract(newAlloc).max(BigDecimal.ZERO);

        return CustomerReturnPreviewResponseDto.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomerName())
                .totalReturnAmount(runningReturnTotal)
                .previousAllocatedAmount(currentAlloc)
                .releasedAllocationAmount(releasedAlloc)
                .newEffectivePayableAmount(newEffectivePayable)
                .newAllocatedAmount(newAlloc)
                .newOutstandingAmount(newOutstanding)
                .lines(previewLines)
                .build();
    }

    public CustomerReturnResponseDto createReturn(String idempotencyKey, Long invoiceId, Long actorId, CustomerReturnRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "CUSTOMER_RETURN",
                "Customer return: " + request.getReason(),
                "POST",
                "/api/v1/invoices/" + invoiceId + "/returns",
                request,
                CustomerReturnResponseDto.class,
                event -> doCreateReturn(event, invoiceId, actorId, request)
        );
    }

    private CustomerReturnResponseDto doCreateReturn(BusinessEvent event, Long invoiceId, Long actorId, CustomerReturnRequest request) {
        // 1. Fetch & Lock Customer first (Party Lock Ordering)
        SalesInvoice invoiceUnchecked = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + invoiceId));

        Customer customer = customerRepository.findByIdWithLock(invoiceUnchecked.getCustomer().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + invoiceUnchecked.getCustomer().getId()));

        // 2. Lock Invoice
        SalesInvoice invoice = salesInvoiceRepository.findByIdWithLock(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesInvoice not found with ID: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new AppException("INVALID_INVOICE_STATUS", "Returns can only be recorded for POSTED invoices", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 3. Optimistic version check
        if (request.getExpectedInvoiceVersion() != null && !request.getExpectedInvoiceVersion().equals(invoice.getVersion())) {
            throw new StaleVersionException("The invoice has been modified by another transaction. Reload before creating return.", invoice.getVersion());
        }

        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one return line is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        LocalDate returnDate = request.getReturnDate() != null ? request.getReturnDate() : businessClock.today();

        Map<Long, SalesInvoiceLine> lineMap = invoice.getLines().stream()
                .collect(Collectors.toMap(SalesInvoiceLine::getId, Function.identity()));

        List<CustomerReturnLine> returnLines = new ArrayList<>();
        List<StockPostingLine> stockPostingLines = new ArrayList<>();
        BigDecimal totalReturnAmount = BigDecimal.ZERO;

        for (CustomerReturnLineRequest lineReq : request.getLines()) {
            SalesInvoiceLine line = lineMap.get(lineReq.getInvoiceLineId());
            if (line == null) {
                throw new AppException("INVALID_INVOICE_LINE",
                        "Line ID " + lineReq.getInvoiceLineId() + " does not belong to invoice " + invoice.getInvoiceNumber(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            int remaining = line.getRemainingReturnableQuantity();
            if (lineReq.getQuantity() > remaining) {
                throw new AppException("EXCESS_RETURN_QUANTITY",
                        "Requested return quantity (" + lineReq.getQuantity() + ") exceeds remaining returnable quantity (" + remaining + ") for SKU " + line.getSku(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Update line returned quantity
            line.setReturnedQuantity(line.getReturnedQuantity() + lineReq.getQuantity());
            salesInvoiceLineRepository.save(line);

            ValuationResult valuation = resolveValuation(line, request.getValuationPolicy(), lineReq.getValuationBasis());
            BigDecimal lineAmount = MoneyPolicy.roundMoney(valuation.unitPrice.multiply(BigDecimal.valueOf(lineReq.getQuantity())));
            totalReturnAmount = totalReturnAmount.add(lineAmount);

            BigDecimal taxable = null;
            BigDecimal tax = null;
            if (line.isTaxBreakdownAvailable() && line.getGstRatePct() != null) {
                taxable = MoneyPolicy.computeTaxableFromInclusive(lineAmount, line.getGstRatePct());
                tax = MoneyPolicy.roundMoney(lineAmount.subtract(taxable));
            }

            ProductVariant variant = line.getVariant();

            returnLines.add(CustomerReturnLine.builder()
                    .invoiceLine(line)
                    .variant(variant)
                    .returnedQuantity(lineReq.getQuantity())
                    .unitPrice(valuation.unitPrice)
                    .totalAmount(lineAmount)
                    .valuationBasis(valuation.basis)
                    .gstRatePct(line.getGstRatePct())
                    .taxableAmount(taxable)
                    .taxAmount(tax)
                    .build());

            stockPostingLines.add(StockPostingLine.builder()
                    .variantId(variant.getId())
                    .quantity(lineReq.getQuantity())
                    .direction(StockMovementDirection.IN)
                    .movementType(StockMovementType.CUSTOMER_RETURN)
                    .sourceLineId(line.getId())
                    .lineNote("Customer return for invoice " + invoice.getInvoiceNumber())
                    .build());
        }

        totalReturnAmount = MoneyPolicy.roundMoney(totalReturnAmount);

        // 4. Generate Return Number (CR-%05d)
        Long nextSeq = customerReturnRepository.getNextReturnSequence();
        String returnNumber = String.format("CR-%05d", nextSeq);

        // 5. Stock Posting (allows inactive variants for historical returns)
        stockPostingService.postMovements(StockPostingRequest.builder()
                .lines(stockPostingLines)
                .businessEventId(event.getId())
                .actorId(actorId)
                .occurredAt(now)
                .sourceEntityType("CUSTOMER_RETURN")
                .sourceEntityId(null) // Assigned after save or referenced by returnNumber
                .reason("Customer return: " + returnNumber)
                .allowInactiveVariants(true)
                .build());

        // 6. Release Excess Payment Allocations if allocated exceeds new effective payable
        BigDecimal newEffectivePayable = invoice.getTotalPayableAmount().subtract(invoice.getTotalReturnedAmount().add(totalReturnAmount)).max(BigDecimal.ZERO);
        BigDecimal releasedAlloc = BigDecimal.ZERO;
        BigDecimal currentAlloc = invoice.getTotalAllocatedAmount() != null ? invoice.getTotalAllocatedAmount() : BigDecimal.ZERO;

        if (currentAlloc.compareTo(newEffectivePayable) > 0) {
            BigDecimal excessToRelease = currentAlloc.subtract(newEffectivePayable);
            log.info("Releasing excess payment allocation of {} on invoice {} due to return {}",
                    excessToRelease, invoice.getInvoiceNumber(), returnNumber);

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

            invoice.setTotalAllocatedAmount(newEffectivePayable);
        }

        // 7. Update Invoice entity
        invoice.setTotalReturnedAmount(MoneyPolicy.roundMoney(invoice.getTotalReturnedAmount().add(totalReturnAmount)));

        BigDecimal finalAlloc = invoice.getTotalAllocatedAmount();
        if (newEffectivePayable.compareTo(BigDecimal.ZERO) == 0 || finalAlloc.compareTo(newEffectivePayable) >= 0) {
            invoice.setSettlementStatus(SettlementStatus.PAID);
        } else if (finalAlloc.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setSettlementStatus(SettlementStatus.PARTIALLY_PAID);
        } else {
            invoice.setSettlementStatus(SettlementStatus.UNPAID);
        }

        invoice.setUpdatedAt(now);
        invoice.setUpdatedBy(actorId);
        salesInvoiceRepository.save(invoice);

        // 8. Save Customer Return entity
        CustomerReturn customerReturn = CustomerReturn.builder()
                .returnNumber(returnNumber)
                .invoice(invoice)
                .customer(customer)
                .returnDate(returnDate)
                .totalReturnAmount(totalReturnAmount)
                .releasedAllocationAmount(releasedAlloc)
                .reason(request.getReason())
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        for (CustomerReturnLine rl : returnLines) {
            customerReturn.addLine(rl);
        }

        CustomerReturn savedReturn = customerReturnRepository.save(customerReturn);

        // 9. Post Customer Ledger Entry (-totalReturnAmount: customer credit / reduces balance)
        customerLedgerService.appendLedgerEntry(
                customer,
                event,
                CustomerLedgerSourceType.CUSTOMER_RETURN,
                savedReturn.getId(),
                savedReturn.getTotalReturnAmount().negate(),
                returnDate,
                "Customer return: " + returnNumber
        );

        return toDto(savedReturn);
    }

    @Transactional(readOnly = true)
    public List<CustomerReturnResponseDto> getReturnsByInvoiceId(Long invoiceId) {
        return customerReturnRepository.findByInvoiceIdOrderByReturnDateAsc(invoiceId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerReturnResponseDto getReturnById(Long returnId) {
        CustomerReturn ret = customerReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer return not found with ID: " + returnId));
        return toDto(ret);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerReturnResponseDto> searchReturns(Long customerId, Long invoiceId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<CustomerReturn> page = customerReturnRepository.searchReturns(customerId, invoiceId, startDate, endDate, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    private ValuationResult resolveValuation(SalesInvoiceLine line, String headerPolicy, String linePolicy) {
        List<SalesInvoiceRevisionLine> revLines = salesInvoiceRevisionLineRepository.findByInvoiceLineIdOrderByRevisionAsc(line.getId());

        boolean priceRevised = false;
        BigDecimal originalPrice = null;

        for (SalesInvoiceRevisionLine rl : revLines) {
            if (rl.getPreviousUnitNetPrice() != null && rl.getNewUnitNetPrice() != null &&
                    rl.getPreviousUnitNetPrice().compareTo(rl.getNewUnitNetPrice()) != 0) {
                priceRevised = true;
            }
            if (originalPrice == null && rl.getPreviousUnitNetPrice() != null && rl.getPreviousUnitNetPrice().compareTo(BigDecimal.ZERO) > 0) {
                originalPrice = rl.getPreviousUnitNetPrice();
            }
        }

        if (originalPrice == null) {
            originalPrice = line.getUnitNetPrice();
        }

        if (priceRevised) {
            String policy = (linePolicy != null && !linePolicy.isBlank()) ? linePolicy : headerPolicy;
            if (policy == null || policy.isBlank()) {
                throw new AppException("RETURN_VALUATION_POLICY_REQUIRED",
                        "Line for SKU " + line.getSku() + " underwent historical price revisions. Specify valuation policy ('ORIGINAL_INVOICE_PRICE' or 'LATEST_REVISED_PRICE').",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if ("ORIGINAL_INVOICE_PRICE".equalsIgnoreCase(policy)) {
                return new ValuationResult(originalPrice, "ORIGINAL_INVOICE_PRICE");
            } else if ("LATEST_REVISED_PRICE".equalsIgnoreCase(policy)) {
                return new ValuationResult(line.getUnitNetPrice(), "LATEST_REVISED_PRICE");
            } else {
                throw new AppException("INVALID_VALUATION_POLICY", "Unsupported valuation policy: " + policy, HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        return new ValuationResult(line.getUnitNetPrice(), "ORIGINAL_INVOICE_PRICE");
    }

    private static class ValuationResult {
        final BigDecimal unitPrice;
        final String basis;

        ValuationResult(BigDecimal unitPrice, String basis) {
            this.unitPrice = unitPrice;
            this.basis = basis;
        }
    }

    public CustomerReturnResponseDto toDto(CustomerReturn ret) {
        List<CustomerReturnLineDto> lineDtos = ret.getLines().stream()
                .map(l -> CustomerReturnLineDto.builder()
                        .id(l.getId())
                        .invoiceLineId(l.getInvoiceLine().getId())
                        .variantId(l.getVariant().getId())
                        .sku(l.getVariant().getSku())
                        .productName(l.getInvoiceLine().getProductName())
                        .sizeLabel(l.getInvoiceLine().getSizeLabel())
                        .returnedQuantity(l.getReturnedQuantity())
                        .unitPrice(l.getUnitPrice())
                        .totalAmount(l.getTotalAmount())
                        .valuationBasis(l.getValuationBasis())
                        .gstRatePct(l.getGstRatePct())
                        .taxableAmount(l.getTaxableAmount())
                        .taxAmount(l.getTaxAmount())
                        .build())
                .toList();

        return CustomerReturnResponseDto.builder()
                .id(ret.getId())
                .returnNumber(ret.getReturnNumber())
                .invoiceId(ret.getInvoice().getId())
                .invoiceNumber(ret.getInvoice().getInvoiceNumber())
                .customerId(ret.getCustomer().getId())
                .customerName(ret.getCustomer().getName())
                .returnDate(ret.getReturnDate())
                .totalReturnAmount(ret.getTotalReturnAmount())
                .releasedAllocationAmount(ret.getReleasedAllocationAmount())
                .reason(ret.getReason())
                .lines(lineDtos)
                .businessEventId(ret.getBusinessEvent().getId())
                .version(ret.getVersion())
                .createdAt(ret.getCreatedAt())
                .build();
    }
}
