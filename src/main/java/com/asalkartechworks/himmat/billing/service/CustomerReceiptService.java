package com.asalkartechworks.himmat.billing.service;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReceiptResponseDto;
import com.asalkartechworks.himmat.billing.dto.InvoiceAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.ReceiptAllocationDto;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerSourceType;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerReceiptComponent;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.ReceiptAllocation;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptComponentRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerReceiptService {

    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerReceiptComponentRepository customerReceiptComponentRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final CustomerRepository customerRepository;
    private final CustomerLedgerService customerLedgerService;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public CustomerReceiptResponseDto createReceipt(String idempotencyKey, Long customerId, Long actorId, CreateCustomerReceiptRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "CUSTOMER_RECEIPT",
                "Customer receipt recording",
                "POST",
                "/api/v1/customers/" + customerId + "/receipts",
                request,
                CustomerReceiptResponseDto.class,
                event -> doCreateReceipt(event, customerId, actorId, request)
        );
    }

    private CustomerReceiptResponseDto doCreateReceipt(BusinessEvent event, Long customerId, Long actorId, CreateCustomerReceiptRequest request) {
        // 1. Lock Customer (Party lock first in lock ordering)
        Customer customer = customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        if (!customer.isActive()) {
            throw new AppException("INACTIVE_CUSTOMER", "Cannot record receipt for inactive customer", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        LocalDate receiptDate = request.getReceiptDate() != null ? request.getReceiptDate() : businessClock.today();
        if (receiptDate.isAfter(businessClock.today())) {
            throw new AppException("FUTURE_RECEIPT_DATE", "Receipt date cannot be in the future", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        CustomerReceipt receipt = processReceiptDirect(
                event,
                customer,
                actorId,
                receiptDate,
                request.getComponents(),
                request.getTotalAmount(),
                request.getAllocations(),
                request.getNotes()
        );

        return toDto(receipt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerReceipt processReceiptDirect(BusinessEvent event,
                                               Customer customer,
                                               Long actorId,
                                               LocalDate receiptDate,
                                               List<PaymentComponentDto> components,
                                               BigDecimal totalAmount,
                                               List<InvoiceAllocationRequest> allocations,
                                               String notes) {
        if (components == null || components.isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one payment component is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Validate component total matches totalAmount
        BigDecimal componentTotal = components.stream()
                .map(PaymentComponentDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (MoneyPolicy.roundMoney(componentTotal).compareTo(MoneyPolicy.roundMoney(totalAmount)) != 0) {
            throw new AppException("VALIDATION_FAILED",
                    "Receipt components total (" + componentTotal + ") must equal totalAmount (" + totalAmount + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = businessClock.now();
        Long nextSeq = customerReceiptRepository.getNextReceiptSequence();
        String receiptNumber = String.format("CREC-%05d", nextSeq);

        BigDecimal totalAllocated = BigDecimal.ZERO;
        List<ReceiptAllocation> allocationsToSave = new ArrayList<>();

        if (allocations != null && !allocations.isEmpty()) {
            for (InvoiceAllocationRequest allocReq : allocations) {
                if (allocReq.getAmount() == null || allocReq.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new AppException("VALIDATION_FAILED", "Allocation amount must be greater than zero", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                SalesInvoice invoice = salesInvoiceRepository.findByIdWithLock(allocReq.getInvoiceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + allocReq.getInvoiceId()));

                if (!invoice.getCustomer().getId().equals(customer.getId())) {
                    throw new AppException("INVALID_INVOICE_CUSTOMER", "Invoice does not belong to this customer", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                if (invoice.getStatus() != InvoiceStatus.POSTED) {
                    throw new AppException("INVALID_INVOICE_STATUS", "Cannot allocate receipt to non-posted invoice", HttpStatus.UNPROCESSABLE_ENTITY);
                }

                BigDecimal outstanding = invoice.getOutstandingAmount();

                if (allocReq.getAmount().compareTo(outstanding) > 0) {
                    throw new AppException("EXCESS_ALLOCATION",
                            "Allocation amount (" + allocReq.getAmount() + ") exceeds invoice outstanding balance (" + outstanding + ")",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }

                totalAllocated = totalAllocated.add(allocReq.getAmount());
                BigDecimal newAllocated = invoice.getTotalAllocatedAmount().add(allocReq.getAmount());
                invoice.setTotalAllocatedAmount(MoneyPolicy.roundMoney(newAllocated));

                if (invoice.getTotalAllocatedAmount().compareTo(invoice.getEffectivePayableAmount()) >= 0) {
                    invoice.setSettlementStatus(SettlementStatus.PAID);
                } else if (invoice.getTotalAllocatedAmount().compareTo(BigDecimal.ZERO) > 0) {
                    invoice.setSettlementStatus(SettlementStatus.PARTIALLY_PAID);
                } else {
                    invoice.setSettlementStatus(SettlementStatus.UNPAID);
                }

                invoice.setUpdatedAt(now);
                invoice.setUpdatedBy(actorId);
                salesInvoiceRepository.save(invoice);

                allocationsToSave.add(ReceiptAllocation.builder()
                        .invoice(invoice)
                        .allocatedAmount(MoneyPolicy.roundMoney(allocReq.getAmount()))
                        .businessEvent(event)
                        .createdAt(now)
                        .build());
            }

            if (totalAllocated.compareTo(totalAmount) > 0) {
                throw new AppException("EXCESS_TOTAL_ALLOCATION",
                        "Sum of allocations (" + totalAllocated + ") cannot exceed receipt total amount (" + totalAmount + ")",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        BigDecimal unallocated = MoneyPolicy.roundMoney(totalAmount.subtract(totalAllocated));

        CustomerReceipt receipt = CustomerReceipt.builder()
                .receiptNumber(receiptNumber)
                .customer(customer)
                .receiptDate(receiptDate)
                .totalAmount(MoneyPolicy.roundMoney(totalAmount))
                .unallocatedAmount(unallocated)
                .notes(notes)
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        for (PaymentComponentDto compDto : components) {
            CustomerReceiptComponent comp = CustomerReceiptComponent.builder()
                    .method(compDto.getMethod())
                    .amount(MoneyPolicy.roundMoney(compDto.getAmount()))
                    .onlineReference(compDto.getOnlineReference())
                    .build();
            receipt.addComponent(comp);
        }

        CustomerReceipt savedReceipt = customerReceiptRepository.save(receipt);

        for (ReceiptAllocation alloc : allocationsToSave) {
            alloc.setCustomerReceipt(savedReceipt);
            receiptAllocationRepository.save(alloc);
        }

        // Post to Customer Ledger (-totalAmount, negative = reduces customer debt / creates advance credit)
        customerLedgerService.appendLedgerEntry(
                customer,
                event,
                CustomerLedgerSourceType.RECEIPT,
                savedReceipt.getId(),
                savedReceipt.getTotalAmount().negate(),
                receiptDate,
                "Customer receipt: " + receiptNumber
        );

        return savedReceipt;
    }

    @Transactional(readOnly = true)
    public CustomerReceiptResponseDto getReceiptById(Long id) {
        CustomerReceipt receipt = customerReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerReceipt not found with ID: " + id));
        return toDto(receipt);
    }

    @Transactional(readOnly = true)
    public Page<CustomerReceiptResponseDto> searchReceipts(Long customerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return customerReceiptRepository.searchReceipts(customerId, startDate, endDate, pageable)
                .map(this::toDto);
    }

    public CustomerReceiptResponseDto toDto(CustomerReceipt receipt) {
        List<PaymentComponentDto> compDtos = receipt.getComponents().stream()
                .map(c -> PaymentComponentDto.builder()
                        .method(c.getMethod())
                        .amount(c.getAmount())
                        .onlineReference(c.getOnlineReference())
                        .build())
                .toList();

        List<ReceiptAllocation> allocations = receiptAllocationRepository.findByCustomerReceiptId(receipt.getId());
        List<ReceiptAllocationDto> allocDtos = allocations.stream()
                .map(a -> ReceiptAllocationDto.builder()
                        .id(a.getId())
                        .receiptId(receipt.getId())
                        .receiptNumber(receipt.getReceiptNumber())
                        .invoiceId(a.getInvoice().getId())
                        .invoiceNumber(a.getInvoice().getInvoiceNumber())
                        .allocatedAmount(a.getAllocatedAmount())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return CustomerReceiptResponseDto.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .customerId(receipt.getCustomer().getId())
                .customerName(receipt.getCustomer().getName())
                .receiptDate(receipt.getReceiptDate())
                .totalAmount(receipt.getTotalAmount())
                .unallocatedAmount(receipt.getUnallocatedAmount())
                .notes(receipt.getNotes())
                .components(compDtos)
                .allocations(allocDtos)
                .version(receipt.getVersion())
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .createdBy(receipt.getCreatedBy())
                .updatedBy(receipt.getUpdatedBy())
                .build();
    }
}
